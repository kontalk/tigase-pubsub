/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub;

import java.util.Collection;

import tigase.server.Packet;

/**
 * 
 * @author andrzej
 */
public interface PacketWriter {

	void write(Collection<Packet> packets);

	void write(final Packet packet);

}
