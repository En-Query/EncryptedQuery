/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.encryption;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Paillier cryptosystem.
 * <p>
 * The basic Paillier scheme works as follows. Let N=pq be a RSA modulus where p,q are large primes
 * of roughly the same bit length. The plaintext space is the additive group Z/NZ and the ciphertext
 * space is the multiplicative group (Z/N^2 Z)*. The public key is the value of N while the private
 * key is the factorization of N into p and q. Let lambda(N) = lcm(p-1,q-1) be the Carmichael
 * function of N (the exponent of the multiplicative group of units modulo N):
 * <p>
 * Encryption E(m) for a message m is as follows. Given N and m,
 * <ul>
 * <li>Select a random value r in (Z/NZ)*
 * <li>E(m) = (1 + mN)r^N mod N^2
 * </ul>
 * <p>
 * Decryption D(c) for a ciphertext c is as follows. Given N, its factorization N=pq, and ciphertext
 * c
 * <ul>
 * <li>Set w = lambda(N)^-1 mod N
 * <li>Set x = c^(lambda(N))mod N^2
 * <li>Set y = (x-1)/N
 * <li>Set D(c) = yw mod N
 * </ul>
 * <p>
 * Note that encryption is randomized: encrypting the same message twice will produce two different
 * ciphertext values. However, both ciphertext values will both decrypt to the same original
 * message.
 * <p>
 * Our implementation works faster by performing encryption and decryption modulo p^2 and q^2
 * separately and then combining the results using the Chinese Remainder Theorem.
 * <p>
 * Reference: Paillier, Pascal. "Public-Key Cryptosystems Based on Composite Degree Residuosity
 * Classes." EUROCRYPT'99.
 */

// Our implementation works as follows:
//
// Encryption E(m) for a message m:
//
// * Select a random value r1 in (Z/pZ)*
// * Set e1 = (1 + mN)r1^p mod p^2
// * Select a random value r2 in (Z/qZ)*
// * Set e2 = (1 + mN)r2^q mod q^2
// * Set E(m) to be the unique value e in (Z/N^2Z)* such that e mod
// p^2 = e1 and e mod q^2 = e2.
//
// Decryption D(c) for a ciphetext c:
//
// * Set w1 = -q mod p
// * Set w2 = -p mod q
// * Set c1 = c mod p^2
// * Set c2 = c mod q^2
// * Set x1 = c1^(p-1) mod p^2
// * Set x2 = c2^(q-1) mod q^2
// * Set y1 = (x1-1)/p
// * Set y2 = (x2-1)/q
// * Set d1 = (y1 * w1) mod p
// * Set d2 = (y2 * w2) mod p
// * Set D(c) = the unique value d in Z/NZ such that d mod p = d1 and d
// mod q = d2.

@Component(service = PaillierEncryption.class)
public class PaillierEncryption {
	private static final Logger logger = LoggerFactory.getLogger(PaillierEncryption.class);


	@Reference
	private ModPowAbstraction modPowAbstraction;
	@Reference
	private PrimeGenerator primeGenerator;
	@Reference
	private RandomProvider randomProvider;

	private int primeCertainty = 128;

	public PaillierEncryption() {}

	public PaillierEncryption(ModPowAbstraction modPowAbstraction, PrimeGenerator primeGenerator, RandomProvider randomProvider) {
		Validate.notNull(modPowAbstraction);
		Validate.notNull(primeGenerator);
		Validate.notNull(randomProvider);

		this.modPowAbstraction = modPowAbstraction;
		this.primeGenerator = primeGenerator;
		this.randomProvider = randomProvider;
	}

	@Activate
	public void initialize(Map<String, String> config) {
		primeCertainty = Integer.parseInt(config.getOrDefault(ResponderProperties.CERTAINTY, "128"));
	}

	/**
	 * Creates a Paillier algorithm with all parameters specified.
	 *
	 * @param p First large prime.
	 * @param q Second large prime.
	 * @param bitLength Bit length of the modulus {@code N}.
	 * @throws IllegalArgumentException If {@code p} or {@code q} do not satisfy primality
	 *         constraints.
	 */
	public Paillier make(BigInteger p, BigInteger q, int bitLength) {

		// Verify the prime conditions are satisfied
		BigInteger three = BigInteger.valueOf(3);
		if (p.compareTo(three) < 0 || q.compareTo(three) < 0 || p.equals(q) || !p.isProbablePrime(primeCertainty) || !q.isProbablePrime(primeCertainty)) {
			throw new IllegalArgumentException("p = " + p + " q = " + q + " do not satisfy primality constraints");
		}

		Paillier result = new Paillier();
		result.setBitLength(bitLength);
		setBasePointsAndExponents(result, p, q);
		// setDerivativeElements(result);
		return result;
	}

	/**
	 * Constructs a Paillier algorithm with generated keys.
	 * <p>
	 * The generated keys {@code p} and {@code q} will have half the given modulus bit length, and
	 * the given prime certainty.
	 * <p>
	 * The probability that the generated keys represent primes will exceed (1 -
	 * (1/2)<sup>{@code certainty}</sup>). The execution time of this constructor is proportional to
	 * the value of this parameter.
	 *
	 * @param bitLength The bit length of the resulting modulus {@code N}.
	 * @param certainty The probability that the new {@code p} and {@code q} represent prime
	 *        numbers.
	 * @throws PIRException
	 * @throws IllegalArgumentException If the {@code certainty} is less than the system allowed
	 *         lower bound.
	 */
	public Paillier make(int bitLength, int certainty) throws PIRException {
		return make(bitLength, certainty, -1);
	}

	/**
	 * Constructs a Paillier algorithm with generated keys and optionally ensures a certain bit is
	 * set in the modulus.
	 * <p>
	 * The generated keys {@code p} and {@code q} will have half the given modulus bit length, and
	 * the given prime certainty.
	 * <p>
	 * The probability that the generated keys represent primes will exceed (1 -
	 * (1/2)<sup>{@code certainty}</sup>). The execution time of this constructor is proportional to
	 * the value of this parameter.
	 * <p>
	 * When ensureBitSet > -1 the value of bit "{@code ensureBitSet}" in modulus {@code N} will be
	 * set.
	 *
	 * @param bitLength The bit length of the resulting modulus {@code N}.
	 * @param certainty The probability that the new {@code p} and {@code q} represent prime
	 *        numbers.
	 * @param ensureBitSet index of bit in {@code N} to ensure is set.
	 * @throws PIRException
	 * @throws IllegalArgumentException If the {@code certainty} is less than the system allowed
	 *         lower bound, or the index of {@code ensureBitSet} is greater than the
	 *         {@code bitLength}.
	 */
	public Paillier make(int bitLength, int certainty, int ensureBitSet) throws PIRException {

		if (certainty < primeCertainty) {
			throw new IllegalArgumentException("Input certainty = " + certainty + " is less than allowed system lower bound = " + primeCertainty);
		}

		if (ensureBitSet >= bitLength) {
			throw new IllegalArgumentException("ensureBitSet = " + ensureBitSet + " must be less than bitLengthInput = " + bitLength);
		}

		Paillier result = new Paillier();
		result.setBitLength(bitLength);
		generateKeys(result, certainty, ensureBitSet);
		setDerivativeElements(result);

		// logger.info("Parameters = " + parametersToString());
		return result;
	}


	private void generateKeys(Paillier paillier, int certainty, int ensureBitSet) throws PIRException {
		getKeys(paillier, certainty);

		if (ensureBitSet > -1) {
			while (!paillier.getN().testBit(ensureBitSet)) {
				logger.info("testBit false\n N = " + paillier.getN().toString(2));
				getKeys(paillier, certainty);
			}
			logger.info("testBit true\n N = " + paillier.getN().toString(2));
		}
	}

	private void getKeys(Paillier paillier, int certainty) throws PIRException {
		if (paillier.getBitLength() >= 1024) {
			// prime generation is not expected to fail, but if it does we keep trying until it
			// succeeds
			// while (true) {
			// try {
			BigInteger[] pq = primeGenerator.getPrimePairWithAuxiliaryPrimes(paillier.getBitLength(), certainty);
			setBasePointsAndExponents(paillier, pq[0], pq[1], pq[2], pq[3]);
			// break;
			// } catch (PIRException e) {
			// }
			// }
		} else {
			// Generate the primes
			BigInteger[] pq = primeGenerator.getPrimePair(paillier.getBitLength(), certainty);
			setBasePointsAndExponents(paillier, pq[0], pq[1]);
		}

	}

	private void setBasePointsAndExponents(Paillier paillier, BigInteger p, BigInteger p1, BigInteger q, BigInteger q1) {
		BigInteger pBasePoint;
		BigInteger pMaxExponent;
		BigInteger qBasePoint;
		BigInteger qMaxExponent;
		// BigInteger p = pq[0];
		// BigInteger p1 = pq[1];
		// BigInteger q = pq[2];
		// BigInteger q1 = pq[3];
		BigInteger[] pBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(p, p1);
		pBasePoint = pBasePointAndMaxExponent[0];
		pMaxExponent = pBasePointAndMaxExponent[1];
		BigInteger[] qBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(q, q1);
		qBasePoint = qBasePointAndMaxExponent[0];
		qMaxExponent = qBasePointAndMaxExponent[1];

		paillier.setPBasePoint(pBasePoint);
		paillier.setPMaxExponent(pMaxExponent);
		paillier.setQBasePoint(qBasePoint);
		paillier.setQMaxExponent(qMaxExponent);
		paillier.setN(p.multiply(q));
		paillier.setP(p);
		paillier.setQ(q);
	}

	/**
	 * Called to initialize the Paillier object's base points and maximum exponents for the order
	 * p-1 subgroup of (Z/p^2Z)* and the order q-1 subgroup of (Z/p^2Z)*. This function is called
	 * during Paillier object construction in the case when auxiliary primes are not being used.
	 *
	 * Each of the two base points is generated by raising a random value to the p-th or q-th power
	 * and does not necessarily generate the entire respective subgroup. The maximum exponents are
	 * set to p-1 and q-1.
	 * 
	 * @param q
	 * @param p
	 */
	private void setBasePointsAndExponents(Paillier paillier, BigInteger p, BigInteger q) {
		BigInteger pSquared = p.multiply(p);
		BigInteger qSquared = q.multiply(q);

		BigInteger pBasePoint;
		// setting base point to (rand)^p mod p^2
		// this depends only on rand mod p so we can pick rand < p
		do {
			pBasePoint = new BigInteger(p.bitLength(), randomProvider.getSecureRandom());
		} while (pBasePoint.compareTo(BigInteger.ONE) <= 0 || pBasePoint.compareTo(p) >= 0);

		pBasePoint = modPowAbstraction.modPow(pBasePoint, p, pSquared);
		BigInteger pMaxExponent = p.subtract(BigInteger.ONE);

		// doing the same for q
		BigInteger qBasePoint;
		do {
			qBasePoint = new BigInteger(q.bitLength(), randomProvider.getSecureRandom());
		} while (qBasePoint.compareTo(BigInteger.ONE) <= 0 || qBasePoint.compareTo(q) >= 0);

		qBasePoint = modPowAbstraction.modPow(qBasePoint, q, qSquared);
		BigInteger qMaxExponent = q.subtract(BigInteger.ONE);

		// update Paillier with the results

		paillier.setN(p.multiply(q));
		paillier.setP(p);
		paillier.setQ(q);
		paillier.setPBasePoint(pBasePoint);
		paillier.setPMaxExponent(pMaxExponent);
		paillier.setQBasePoint(qBasePoint);
		paillier.setQMaxExponent(qMaxExponent);
	}

	/**
	 * Returns an ordered pair consisting of a choice of basepoint and maximum exponent for either
	 * the order p1 subgroup of (Z/p^2Z)* or the order q1 subgroup of (Z/p^2Z)*. This function is
	 * called during Paillier object construction in the case when auxiliary primes are also
	 * generated.
	 *
	 * The base point is generated by raising a random value to the p-th or q-th power; it is then
	 * checked that the base point has order divisible by p1 or p2. The base points do not
	 * necessarily generate the entire respective subgroup. The maximum exponent is set to the value
	 * of the auxiliary prime p1.
	 *
	 * @return array whose first element is the base point and the second element is the maximum
	 *         exponent
	 */
	private BigInteger[] calcBasePointAndExponentWithAuxiliaryPrime(BigInteger p, BigInteger p1) {
		// checking p1 divides p - 1 and saving (p - 1)/p1 for later
		BigInteger[] divmod = p.subtract(BigInteger.ONE).divideAndRemainder(p1);
		BigInteger div = divmod[0];
		BigInteger rem = divmod[1];
		if (rem.compareTo(BigInteger.ZERO) != 0) {
			throw new IllegalArgumentException("auxiliary prime fails to divide prime minus one");
		}
		BigInteger pSquared = p.multiply(p);
		// exponent and random base point for p^2
		BigInteger maxExponent = p1;
		BigInteger[] result = null;
		while (result == null) {
			BigInteger basePoint = new BigInteger(p.bitLength(), randomProvider.getSecureRandom());
			if (basePoint.compareTo(BigInteger.ONE) <= 0 || basePoint.compareTo(p) >= 0) continue;

			basePoint = modPowAbstraction.modPow(basePoint, p, pSquared);
			BigInteger tmp = modPowAbstraction.modPow(basePoint, div, pSquared);
			if (tmp.compareTo(BigInteger.ONE) == 0) continue;

			result = new BigInteger[] {basePoint, maxExponent};
		}
		return result;
	}

	/**
	 * Returns an encryption of a given message.
	 * 
	 * @param m the value to be encrypted.
	 * @return the encrypted value
	 * @throws PIRException If {@code m} is not less than @{code N}.
	 */
	public BigInteger encrypt(Paillier paillier, BigInteger m) throws PIRException {
		BigInteger p = paillier.getP();
		BigInteger q = paillier.getQ();

		// Generate a random value in (Z/NZ)*. We generate the mod p and mod q portions separately.
		final Random rnd = randomProvider.getSecureRandom();

		BigInteger r1 = new BigInteger(p.bitLength(), rnd).mod(p);
		while (r1.equals(BigInteger.ZERO) || r1.equals(BigInteger.ONE) || r1.mod(p).equals(BigInteger.ZERO) || r1.mod(q).equals(BigInteger.ZERO)) {
			r1 = new BigInteger(p.bitLength(), rnd).mod(p);
		}

		BigInteger r2 = new BigInteger(q.bitLength(), rnd).mod(q);
		while (r2.equals(BigInteger.ZERO) || r2.equals(BigInteger.ONE) || r2.mod(p).equals(BigInteger.ZERO) || r2.mod(q).equals(BigInteger.ZERO)) {
			r2 = new BigInteger(q.bitLength(), rnd).mod(q);
		}

		return encrypt(paillier, m, r1, r2);
	}

	/**
	 * Returns an encryption of a a given message using the given random values.
	 *
	 * @param m the value to be encrypted.
	 * @param r1 the mod {@code p} part of the random base to use in the Pailler encryption.
	 * @param r2 the mod {@code q} part of the random base to use in the Pailler encryption.
	 * @return the encrypted value.
	 * @throws PIRException If {@code m} is not less than @{code N}.
	 */
	public BigInteger encrypt(Paillier paillier, BigInteger m, BigInteger r1, BigInteger r2) throws PIRException {
		BigInteger N = paillier.getN();

		if (m.compareTo(N) >= 0) {
			throw new PIRException("m  = " + m.toString(2) + " is greater than or equal to N = " + N.toString(2));
		}

		BigInteger NSquared = N.multiply(N);
		BigInteger p = paillier.getP();
		BigInteger q = paillier.getQ();
		BigInteger pSquared = p.multiply(p);
		BigInteger qSquared = q.multiply(q);
		ChineseRemainder crtNSquared = new ChineseRemainder(pSquared, qSquared);

		// E(m) = (1 + mN) * CRT(r1^p mod p^2, r2^q mod q^2)
		BigInteger term1 = m.multiply(N).add(BigInteger.ONE).mod(NSquared);
		BigInteger u = modPowAbstraction.modPow(r1, p, pSquared);
		BigInteger v = modPowAbstraction.modPow(r2, q, qSquared);
		BigInteger term2 = crtNSquared.combine(u, v, NSquared);

		return term1.multiply(term2).mod(NSquared);
	}

	/**
	 * Returns the plaintext message for a given ciphertext.
	 *
	 * @param c an encrypted value.
	 * @return the corresponding plaintext value.
	 */
	public BigInteger decrypt(Paillier paillier, BigInteger c) {
		// x = c^(p-1) mod p^2, y = (x - 1)/p, z = y * ((p-1)*q)^-1 mod p
		// x' = c^(q-1) mod q^2, y' = (x'- 1)/q, z' = y' * ((q-1)*p)^-1 mod q
		// d = crt.combine(z, z')
		BigInteger p = paillier.getP();
		BigInteger q = paillier.getQ();
		ChineseRemainder crtN = new ChineseRemainder(p, q);
		BigInteger pSquared = p.multiply(p);
		BigInteger qSquared = q.multiply(q);
		BigInteger pMinusOne = p.subtract(BigInteger.ONE);
		BigInteger qMinusOne = q.subtract(BigInteger.ONE);
		BigInteger wp = p.subtract(q).modInverse(p);
		BigInteger wq = q.subtract(p).modInverse(q);
		BigInteger cModPSquared = c.mod(pSquared);
		BigInteger cModQSquared = c.mod(qSquared);
		BigInteger xp = modPowAbstraction.modPow(cModPSquared, pMinusOne, pSquared);
		BigInteger xq = modPowAbstraction.modPow(cModQSquared, qMinusOne, qSquared);
		BigInteger yp = xp.subtract(BigInteger.ONE).divide(p);
		BigInteger yq = xq.subtract(BigInteger.ONE).divide(q);
		BigInteger zp = yp.multiply(wp).mod(p);
		BigInteger zq = yq.multiply(wq).mod(q);
		BigInteger d = crtN.combine(zp, zq, paillier.getN());
		return d;
	}

	// private String parametersToString() {
	// return "p = " + p.intValue() + " q = " + q.intValue() + " N = " + N.intValue() + " NSquared =
	// " + NSquared.intValue()
	// + " bitLength = " + bitLength;
	// }

	private void setDerivativeElements(Paillier paillier) {
		paillier.setNSquared(paillier.getN().multiply(paillier.getN()));
		paillier.setPSquared(paillier.getP().multiply(paillier.getP()));
		paillier.setQSquared(paillier.getQ().multiply(paillier.getQ()));
		paillier.setCrtNSquared(new ChineseRemainder(paillier.getPSquared(), paillier.getQSquared()));
		paillier.setCrtN(new ChineseRemainder(paillier.getP(), paillier.getQ()));
		paillier.setPMinusOne(paillier.getP().subtract(BigInteger.ONE));
		paillier.setQMinusOne(paillier.getQ().subtract(BigInteger.ONE));
		paillier.setWp(paillier.getP().subtract(paillier.getQ()).modInverse(paillier.getP()));
		paillier.setWq(paillier.getQ().subtract(paillier.getP()).modInverse(paillier.getQ()));
	}
}
