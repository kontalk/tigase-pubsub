/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.adhoc;

import java.util.List;

import tigase.server.Packet;
import tigase.xml.Element;

/**
 * 
 * @author andrzej
 */
public interface AdHocScriptCommandManager {

	List<Element> getCommandListItems(final String senderJid, final String toJid);

	List<Packet> process(Packet packet);

}
