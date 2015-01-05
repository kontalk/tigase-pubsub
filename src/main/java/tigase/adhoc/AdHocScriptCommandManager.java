/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.adhoc;

import java.util.List;

import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;

public interface AdHocScriptCommandManager {

	List<Element> getCommandListItems(final JID senderJid, final JID toJid);

	boolean canCallCommand(JID jid, String commandId);

	List<Packet> process(Packet packet);

}
