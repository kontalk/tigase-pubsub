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

import java.security.SecureRandom;
import java.util.logging.Logger;

import tigase.util.JIDUtils;

public class Utils {

	private final static String CHARSET = "abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	protected static Logger log = Logger.getLogger(Utils.class.getName());

	private static SecureRandom numberGenerator;

	public static String longToTime(long time) {
		long hours = time / 3600000;
		long mins = (time - (hours * 3600000)) / 60000;
		long secs = (time - ((hours * 3600000) + (mins * 60000))) /	1000;
		long millis = (time - ((hours * 3600000) + (mins * 60000) + secs * 1000));
		return "" + 
						(hours > 0 ? hours + "h, " : "") +
						(mins > 0 ? mins + "m, " : "") +
						(secs > 0 ? secs + "sec, " : "") +
						(millis > 0 ? millis + "ms" : "");
	}

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
		String result = "";
		for (int i = 0; i < 16; i++) {
			int a = ng.nextInt(CHARSET.length());
			result += CHARSET.charAt(a);
		}
		return result;
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
