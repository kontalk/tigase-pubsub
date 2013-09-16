package tigase.component2;

import java.util.Collection;

import tigase.server.Packet;

public interface PacketWriter {

	void write(Collection<Packet> elements);

	void write(final Packet element);

}
