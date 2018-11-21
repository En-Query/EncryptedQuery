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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to generate the primes used in the Paillier cryptosystem
 * <p>
 * This class will either:
 * <p>
 * (1) Generate the primes according to Java's BigInteger prime generation methods, which satisfy
 * ANSI X9.80
 * <p>
 * or
 * <p>
 * (2) Bolster Java BigInteger's prime generation to meet the requirements of NIST SP 800-56B
 * ("Recommendation for Pair-Wise Key Establishment Schemes Using Integer Factorization
 * Cryptography") and FIPS 186-4 ("Digital Signature Standard (DSS)") for key generation using
 * probable primes.
 * <p>
 * Relevant page: SP 800-56B: p30
 * http://csrc.nist.gov/publications/nistpubs/800-56B/sp800-56B.pdf#page=30 Heading: 5.4 Prime
 * Number Generators
 * <p>
 * Relevant pages FIPS 186-4: p50-p53, p55, p71: Sections B.3.1, B.3.3
 * <p>
 * http://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf#page=61
 * <p>
 * http://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf#page=80
 * <p>
 * Headings of most interest: Table C.2 "Minimum number of rounds of M-R testing when generating
 * primes for use in RSA Digital Signatures" and "The primes p and q shall be selected with the
 * following constraints"
 * 
 */
@Component(service = PrimeGenerator.class)
public class PrimeGenerator {
	private static final Logger logger = LoggerFactory.getLogger(PrimeGenerator.class);

	private static final BigDecimal SQRT_2 = BigDecimal.valueOf(Math.sqrt(2));

	// TODO: check concurrency and size of these caches
	private final HashMap<Integer, BigInteger> lowerBoundCache = new HashMap<>();
	private final HashMap<Integer, BigInteger> minimumDifferenceCache = new HashMap<>();

	@Reference
	private ModPowAbstraction modPowAbstraction;
	@Reference
	private RandomProvider randomProvider;

	public PrimeGenerator() {}

	public PrimeGenerator(ModPowAbstraction modPowAbstraction, RandomProvider randomProvider) {
		Validate.notNull(modPowAbstraction);
		Validate.notNull(randomProvider);
		this.modPowAbstraction = modPowAbstraction;
		this.randomProvider = randomProvider;
	}

	/**
	 * Method to generate a single prime
	 * <p>
	 * Will optionally ensure that the prime meets the requirements in NIST SP 800-56B and FIPS
	 * 186-4
	 * <p>
	 * NOTE: bitLength corresponds to the FIPS 186-4 nlen parameter
	 */
	public BigInteger getSinglePrime(int bitLength, int certainty) {
		BigInteger p;

		final Random rnd = randomProvider.getSecureRandom();
		logger.debug("bitLength " + bitLength + " certainty " + certainty + " random " + rnd);

		// Calculate the number of Miller-Rabin rounds that we need to do
		// to comply with FIPS 186-4, Appendix C.2
		int numRounds = calcNumMillerRabinRounds(bitLength);

		// Calculate the lower bound (\sqrt(2))(2^(bitLength/2) – 1)) for use in FIPS 186-4 B.3.3,
		// step 4.4
		BigInteger lowerBound;
		if (!lowerBoundCache.containsKey(bitLength)) {
			lowerBound = SQRT_2.multiply(BigDecimal.valueOf(2).pow((bitLength / 2) - 1)).toBigInteger();
			lowerBoundCache.put(bitLength, lowerBound);
		} else {
			lowerBound = lowerBoundCache.get(bitLength);
		}

		// Complete FIPS 186-4 B.3.3, steps 4.2 - 4.5
		while (true) {
			// FIPS 186-4 B.3.3, step 4.2 and 4.3
			// 4.2: Obtain a string p of (bitLength/2) bits from an RBG that supports the
			// security_strength.
			// 4.3: If (p is not odd), then p = p + 1. -- BigInteger hands us a prime (hence odd)
			// with the call below
			p = new BigInteger(bitLength / 2, certainty, rnd);

			// FIPS 186-4 B.3.3, step 4.4
			// 4.4: If p < (\sqrt(2))(2^(bitLength/2) – 1)), then go to step 4.2.
			if (p.compareTo(lowerBound) > -1) {
				// FIPS 186-4, step 4.5
				if (passesMillerRabin(p, numRounds)) {
					// We have winner
					break;
				}
			}
		}

		return p;
	}

	/**
	 * Method to generate a second prime, q, in relation to a (p,q) RSA key pair
	 * <p>
	 * Will optionally ensure that the prime meets the requirements in NIST SP 800-56B and FIPS
	 * 186-4
	 * <p>
	 * NOTE: bitLength corresponds to the FIPS 186-4 nlen parameter
	 */
	public BigInteger getSecondPrime(int bitLength, int certainty, BigInteger p) {
		BigInteger q;

		final Random rnd = randomProvider.getSecureRandom();
		logger.debug("bitLength " + bitLength + " certainty " + certainty + " random " + rnd);

		// Calculate the number of Miller-Rabin rounds that we need to do
		// to comply with FIPS 186-4, Appendix C.2
		int numRounds = calcNumMillerRabinRounds(bitLength);

		// Calculate the lower bound (\sqrt(2))(2^(bitLength/2) – 1)) for use in FIPS 186-4 B.3.3,
		// step 5.5
		BigInteger lowerBound;
		if (!lowerBoundCache.containsKey(bitLength)) {
			lowerBound = SQRT_2.multiply(BigDecimal.valueOf(2).pow((bitLength / 2) - 1)).toBigInteger();
			lowerBoundCache.put(bitLength, lowerBound);
		} else {
			lowerBound = lowerBoundCache.get(bitLength);
		}

		// Compute the minimumDifference 2^((bitLength/2) – 100) for use in FIPS 186-4 B.3.3, step
		// 5.4
		BigInteger minimumDifference;
		if (!minimumDifferenceCache.containsKey(bitLength)) {
			minimumDifference = BigDecimal.valueOf(2).pow(bitLength / 2 - 100).toBigInteger();
			minimumDifferenceCache.put(bitLength, minimumDifference);
		} else {
			minimumDifference = minimumDifferenceCache.get(bitLength);
		}

		// Complete FIPS 186-4 B.3.3, steps 5.2 - 5.6
		while (true) {
			// FIPS 186-4 B.3.3, step 5.2 and 5.3
			// 5.2: Obtain a string q of (bitLength/2) bits from an RBG that supports the
			// security_strength.
			// 5.3: If (q is not odd), then q = q + 1. -- BigInteger hands us a prime (hence odd)
			// with the call below
			q = new BigInteger(bitLength / 2, certainty, rnd);

			// FIPS 186-4 B.3.3, step 5.4 & 5.5
			// 5.4 If (|p – q| ≤ 2^((bitLength/2) – 100), then go to step 5.2
			// 5.5: If q < (\sqrt(2))(2^(bitLength/2) – 1)), then go to step 5.2.
			BigInteger absDiff = (p.subtract(q)).abs();
			if ((q.compareTo(lowerBound) > -1) && (absDiff.compareTo(minimumDifference) > 0)) {
				// FIPS 186-4, step 5.6
				if (passesMillerRabin(q, numRounds)) {
					// We have winner
					break;
				}
			}
		}
		return q;
	}

	/**
	 * This method returns a two-long array containing a viable RSA p and q meeting FIPS 186-4 and
	 * SP 800-56B
	 */
	public BigInteger[] getPrimePair(int bitLength, int certainty) {
		BigInteger[] toReturn = {null, null};

		toReturn[0] = getSinglePrime(bitLength, certainty);
		toReturn[1] = getSecondPrime(bitLength, certainty, toReturn[0]);

		return toReturn;
	}

	/**
	 * Method to return the number of Miller-Rabin rounds that we need to perform when generating
	 * probable primes in order to be FIPS 186-4 compliant in the case of bitLength = 1024, 2048, or
	 * 3072. The values are from FIPS 186-4 Table C.2. For other prime sizes we try to return a
	 * reasonable value.
	 * <p>
	 * We ignore any Miller-Rabin rounds that the Java BigInteger library may have already done.
	 */
	private int calcNumMillerRabinRounds(int bitLength) {
		int numRounds;

		if (bitLength >= 3072) {
			numRounds = 4;
		} else {
			numRounds = 5;
		}

		return numRounds;
	}

	/**
	 * Method to generate a single prime using auxiliary primes
	 * <p>
	 * Follows Algorithm B.3.6 from FIPS 186-4. Will optionally ensure that the prime meets the
	 * requirements in NIST SP 800-56B and FIPS 186-4
	 * <p>
	 * Returns an array containing the final prime p, followed by the first auxiliary prime p1 which
	 * divides p-1
	 * <p>
	 * NOTE: bitLength corresponds to the FIPS 186-4 nlen parameter
	 * <p>
	 * FIPS 186-4 only applies for bitLength=1024, 2048, and 3072. For any other value of bitLength,
	 * use this method with caution.
	 *
	 * @throws IllegalArgumentException If {@code bitLength} is less than 1024
	 * @throws PIRException If an unexpected error occured during prime generation
	 */
	public BigInteger[] getPrimePairWithAuxiliaryPrimes(int bitLength, int certainty) throws PIRException {
		final Random rnd = randomProvider.getSecureRandom();
		logger.debug("bitLength " + bitLength + " certainty " + certainty + " random " + rnd);

		int[] auxPrimeLengths = calcSuggestedAuxiliaryPrimeLengths(bitLength);
		int len1 = auxPrimeLengths[0];
		int len2 = auxPrimeLengths[1];

		// step 4: Generate p
		// step 4.1
		BigInteger Xp1, Xp2;
		Xp1 = new BigInteger(len1, rnd);
		Xp1 = Xp1.setBit(len1 - 1);
		Xp2 = new BigInteger(len2, rnd);
		Xp2 = Xp2.setBit(len2 - 1);
		// step 4.2
		while (true) {
			Xp1 = Xp1.nextProbablePrime();
			int rounds = calcNumMRRoundsForAuxiliaryPrime(bitLength);
			if (passesMillerRabin(Xp1, rounds)) {
				break;
			}
		}
		BigInteger p1 = Xp1;
		while (true) {
			Xp2 = Xp2.nextProbablePrime();
			int rounds = calcNumMRRoundsForAuxiliaryPrime(bitLength);
			if (passesMillerRabin(Xp2, rounds)) {
				break;
			}
		}
		BigInteger p2 = Xp2;
		// step 4.3
		BigInteger[] pResult = getSinglePrimeFromAuxiliaryPrimes(p1, p2, bitLength, certainty);
		BigInteger p = pResult[0];
		BigInteger Xp = pResult[1];
		BigInteger q, q1;
		while (true) {
			// step 5: Generate q
			// step 5.1
			BigInteger Xq1 = new BigInteger(len1, rnd);
			Xq1 = Xq1.setBit(len1 - 1);
			BigInteger Xq2 = new BigInteger(len2, rnd);
			Xq2 = Xq2.setBit(len2 - 1);
			// step 5.2
			while (true) {
				Xq1 = Xq1.nextProbablePrime();
				int rounds = calcNumMRRoundsForAuxiliaryPrime(bitLength);
				if (passesMillerRabin(Xq1, rounds)) {
					break;
				}
			}
			q1 = Xq1;
			while (true) {
				Xq2 = Xq2.nextProbablePrime();
				int rounds = calcNumMRRoundsForAuxiliaryPrime(bitLength);
				if (passesMillerRabin(Xq2, rounds)) {
					break;
				}
			}
			BigInteger q2 = Xq2;
			// step 5.3
			BigInteger[] qResult = getSinglePrimeFromAuxiliaryPrimes(q1, q2, bitLength, certainty);
			q = qResult[0];
			BigInteger Xq = qResult[1];
			// step 6
			// Compute the minimumDifference 2^((bitLength/2) – 100) for use in FIPS 186-4 B.3.6,
			// step 6
			BigInteger minimumDifference;
			if (!minimumDifferenceCache.containsKey(bitLength)) {
				minimumDifference = BigDecimal.valueOf(2).pow(bitLength / 2 - 100).toBigInteger();
				minimumDifferenceCache.put(bitLength, minimumDifference);
			} else {
				minimumDifference = minimumDifferenceCache.get(bitLength);
			}
			BigInteger absDiff = (p.subtract(q)).abs();
			if (absDiff.compareTo(minimumDifference) <= 0) {
				continue; // go to step 5
			}
			absDiff = (Xp.subtract(Xq)).abs();
			if (absDiff.compareTo(minimumDifference) <= 0) {
				continue; // go to step 5
			}
			break; // goto to step 7
		}
		// (step 7: zero out internal variables)
		// step 8
		return new BigInteger[] {p, p1, q, q1};
	}

	/**
	 * Method to generate a single probable prime from two auxiliary primes, following algorithm C.9
	 * from FIPS 186-4.
	 * <p>
	 * Will optionally ensures that the prime meets the requirements from FIPS 186-4.
	 * <p>
	 * FIPS 186-4 only applies for bitLength=1024, 2048, and 3072. For any other value of bitLength,
	 * use this method with caution.
	 */
	public BigInteger[] getSinglePrimeFromAuxiliaryPrimes(BigInteger r1, BigInteger r2, int bitLength, int certainty) throws PIRException {
		final Random rnd = randomProvider.getSecureRandom();
		// step 1
		BigInteger twoR1 = r1.add(r1);
		// if gcd(2*r1, r2) != 1 return (False, 0, 0)
		if (twoR1.gcd(r2).compareTo(BigInteger.ONE) != 0) {
			throw new IllegalArgumentException("2*r1 and r2 fail to be relatively prime");
		}
		// step 2
		// R = inverse_mod(r2, 2*r1) * r2 - inverse_mod(2*r1, r2) * 2*r1
		BigInteger tmp1 = r2.modInverse(twoR1).multiply(r2);
		BigInteger tmp2 = twoR1.modInverse(r2).multiply(twoR1);
		BigInteger R = tmp1.subtract(tmp2);
		// at this point we should have (R % (2*r1)) == 1 and (R % r2) == r2 - 1
		while (true) {
			// step 3
			// generate X with sqrt(2)*2**(nlen/2-1) <= X <= 2**(nlen/2)-1
			BigInteger lowerBound;
			if (!lowerBoundCache.containsKey(bitLength)) {
				lowerBound = SQRT_2.multiply(BigDecimal.valueOf(2).pow((bitLength / 2) - 1)).toBigInteger();
				lowerBoundCache.put(bitLength, lowerBound);
			} else {
				lowerBound = lowerBoundCache.get(bitLength);
			}
			BigInteger X;
			while (true) {
				X = new BigInteger(bitLength / 2, rnd);
				if (X.compareTo(lowerBound) >= 0) {
					break;
				}
			}
			// step 4
			// Y = X + ((R-X) % (2*r1*r2))
			BigInteger twoR1R2 = twoR1.multiply(r2);
			BigInteger Y = X.add(R.subtract(X).mod(twoR1R2));
			// step 5
			int i = 0;
			while (true) {
				// step 6
				// if Y >= 2^(nlen/2), go to step 3
				if (Y.bitLength() >= bitLength / 2 + 1) {
					break; // go to step 3
				}
				// step 7
				// FIPS 186-4 says to check gcd(Y-1, e) == 1, but there is no
				// encryption exponent to check
				if (Y.isProbablePrime(certainty)) {
					int numRounds = calcNumMillerRabinRounds(bitLength);
					// Run however many more rounds of Miller-Rabin are needed
					if (passesMillerRabin(Y, numRounds)) {
						// we have a winner
						return new BigInteger[] {Y, X};
					}
				}
				// step 8
				i += 1;
				// step 9
				// if i >= 5 * (bitLength / 2), return (FAILURE, 0, 0)
				if (i >= 5 * (bitLength / 2)) {
					throw new PIRException("prime generation failed with i >= 5*(bitlength/2)");
				}
				// step 10
				Y = Y.add(twoR1R2);
				// going back to step 6
			}
			// going back to step 3
		}
	}

	/**
	 * Method to return the number of Miller-Rabin rounds that we need for auxiliary primes in
	 * addition to BigInteger's own primality testing
	 */
	private int calcNumMRRoundsForAuxiliaryPrime(int bitLength) {
		// based on FIPS 186-4 Table C.2
		int rounds = 28;
		if (bitLength >= 3072) {
			rounds = 41;
		} else if (bitLength >= 2048) {
			rounds = 38;
		}
		return rounds;
	}

	/**
	 * Returns suggested lengths for the two auxiliary primes. When bitLength = 1024, 2048, or 3072,
	 * the values are based on FIPS 186-4 Table B.1. For other values > 1024, we try to return
	 * reasonable values.
	 *
	 * @throws IllegalArgumentException If {@code bitLength} is smaller than 1024
	 */
	private int[] calcSuggestedAuxiliaryPrimeLengths(int bitLength) {
		int len1, len2;
		// we pick len1 small to speed up fixed-base point query generation
		if (bitLength < 1024) {
			throw new IllegalArgumentException("bitLength (" + bitLength + ") must be at least 1024");
		} else if (bitLength < 2048) {
			len1 = 256;
			len2 = 239;
		} else if (bitLength < 3072) {
			len1 = 256;
			len2 = 750;
		} else // if bitLength >= 3072
		{
			len1 = 256;
			len2 = 1261;
		}
		return new int[] {len1, len2};
	}

	/**
	 * Returns true iff this BigInteger passes the specified number of Miller-Rabin tests.
	 * <p>
	 * This test is taken from the FIPS 186-4, C.3.1
	 * <p>
	 * The following assumptions are made:
	 * <p>
	 * This BigInteger is a positive, odd number greater than 2. iterations<=50.
	 */
	private boolean passesMillerRabin(BigInteger w, int iterations) {
		final Random rnd = randomProvider.getSecureRandom();
		// Find a and m:
		// a is the largest int such that 2^a | (w-1)
		// and m = (w-1) / 2^a
		BigInteger wMinusOne = w.subtract(BigInteger.ONE);
		BigInteger m = wMinusOne;
		int a = m.getLowestSetBit();
		m = m.shiftRight(a);

		for (int i = 0; i < iterations; i++) {
			// Generate a random b, of length len(w) bits, such that 1 < b < (w-1), steps 4.1-4.2
			BigInteger b = new BigInteger(w.bitLength(), rnd);
			while (b.compareTo(BigInteger.ONE) <= 0 || b.compareTo(w) >= 0) {
				b = new BigInteger(w.bitLength(), rnd);
			}

			// Construct z = b^m mod w, step 4.3
			int j = 0;
			BigInteger z = modPowAbstraction.modPow(b, m, w);
			while (!((j == 0 && z.equals(BigInteger.ONE)) || z.equals(wMinusOne))) // step 4.4-4.5
			{
				if (j > 0 && z.equals(BigInteger.ONE) || ++j == a) {
					return false;
				}
				z = modPowAbstraction.modPow(z, BigInteger.valueOf(2), w); // step 4.5.1
			}
		}
		return true;
	}
}
