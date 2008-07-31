package tigase.pubsub.modules;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import tigase.pubsub.AbstractNodeConfig;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

public class XsltTool {

	private final SimpleParser parser = SingletonFactory.getParserInstance();

	private TransformerFactory tFactory = TransformerFactory.newInstance();

	public List<Element> transform(final Element item, AbstractNodeConfig nodeConfig) throws TransformerException, IOException {
		Source xsltSource;
		final String bodyXsltUrl = nodeConfig.getBodyXslt();
		final String bodyXsltEmbedded = nodeConfig.getBodyXsltEmbedded();
		if (bodyXsltEmbedded != null && bodyXsltEmbedded.length() > 1) {
			Reader reader = new StringReader(bodyXsltEmbedded);
			xsltSource = new StreamSource(reader);
		} else if (bodyXsltUrl != null && bodyXsltUrl.length() > 1) {
			URL x = new URL(bodyXsltUrl);
			xsltSource = new StreamSource(x.openStream());
		} else {
			return null;
		}
		return transform(item, xsltSource);
	}

	private List<Element> transform(final Element item, Source xslt) throws TransformerException {
		Transformer transformer = tFactory.newTransformer(xslt);
		Reader reader = new StringReader(item.toString());

		StringWriter writer = new StringWriter();
		transformer.transform(new StreamSource(reader), new StreamResult(writer));
		char[] data = writer.toString().toCharArray();

		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);
		Queue<Element> q = domHandler.getParsedElements();

		ArrayList<Element> result = new ArrayList<Element>();
		result.addAll(q);
		return result;
	}

}
