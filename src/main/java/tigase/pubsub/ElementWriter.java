package tigase.pubsub;

import java.util.Collection;

import tigase.xml.Element;

public interface ElementWriter {

	void write(Collection<Element> elements);

	void write(final Element element);

}
