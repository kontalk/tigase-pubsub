/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.logging.Logger;

import tigase.util.JIDUtils;

public class Utils {

	protected static Logger log = Logger.getLogger(Utils.class.getName());

	private static SecureRandom numberGenerator;

	public static String asString(String... array) {
		StringBuilder sb = new StringBuilder();
		if (array != null) {
			sb.append("[");
			for (String string : array) {
				sb.append("'");
				sb.append(string);
				sb.append("', ");
			}
			sb.append("]");
		} else {
			sb.append("[null]");
		}
		return sb.toString();
	}

	public static boolean contain(String string, String... array) {
		for (String s : array) {
			if ((s == null && string == null) || (s != null && string != null && string.equals(s)))
				return true;
		}
		return false;
	}

	public static synchronized String createUID() {
		SecureRandom ng = numberGenerator;
		if (ng == null) {
			numberGenerator = ng = new SecureRandom();
		}
		byte[] rnd = new byte[20];
		ng.nextBytes(rnd);
		byte[] tmp = new byte[rnd.length + 1];
		System.arraycopy(rnd, 0, tmp, 1, rnd.length);
		tmp[0] = 0x00;
		BigInteger bi = new BigInteger(tmp);
		return bi.toString(36);
	}

	public static boolean isAllowedDomain(final String jid, final String... domains) {
		log.finer("Checking is " + jid + " allowed to see domains: " + asString(domains));
		if (jid == null || domains == null || domains.length == 0)
			return true;
		final String jidHost = JIDUtils.getNodeHost(jid);
		for (String d : domains) {
			if (jidHost.equals(d))
				return true;
		}
		return false;
	}

	private Utils() {
	}
}
