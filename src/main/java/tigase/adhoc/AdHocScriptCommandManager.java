/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.adhoc;

import java.util.List;

import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * 
 * @author andrzej
 */
public interface AdHocScriptCommandManager {

	List<Element> getCommandListItems(final JID senderJid, final JID toJid);

	List<Packet> process(Packet packet);

}
