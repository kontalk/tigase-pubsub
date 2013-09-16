package tigase.component2.modules;

import tigase.component2.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;

public interface Module {

	String[] getFeatures();

	Criteria getModuleCriteria();

	void process(final Packet packet) throws ComponentException, TigaseStringprepException;
}
