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
package org.enquery.encryptedquery.encryption.nullcipher;

import java.security.KeyPair;

import org.apache.commons.lang3.Validate;

/**
 * Null Cipher public and private key pair
 */
class NullCipherKeyPair {
	private final NullCipherPublicKey pub;
	private final NullCipherPrivateKey priv;

	/**
	 * 
	 */
	public NullCipherKeyPair(NullCipherPublicKey pub, NullCipherPrivateKey priv) {
		Validate.notNull(pub);
		Validate.notNull(priv);
		// XXX test consistency between pub and priv?
		this.pub = pub;
		this.priv = priv;
	}

	public NullCipherKeyPair(KeyPair keyPair) {
		Validate.notNull(keyPair);
		NullCipherPublicKey pub = NullCipherPublicKey.from(keyPair.getPublic());
		Validate.notNull(pub);
		NullCipherPrivateKey priv = NullCipherPrivateKey.from(keyPair.getPrivate());
		Validate.notNull(priv);
		this.pub = pub;
		this.priv = priv;
	}

	public NullCipherPublicKey getPub() {
		return pub;
	}

	public NullCipherPrivateKey getPriv() {
		return priv;
	}
}
