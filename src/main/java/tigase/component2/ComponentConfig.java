package tigase.component2;

import java.util.Map;

import tigase.component2.eventbus.EventBus;
import tigase.xmpp.BareJID;

public abstract class ComponentConfig {

	protected final AbstractComponent<?> component;

	private BareJID serviceName;

	protected ComponentConfig(AbstractComponent<?> component) {
		this.component = component;
	}

	public abstract Map<String, Object> getDefaults(Map<String, Object> params);

	public EventBus getEventBus() {
		return component.getEventBus();
	}

	public BareJID getServiceName() {
		if (serviceName == null) {
			serviceName = BareJID.bareJIDInstanceNS(component.getName());
		}
		return serviceName;
	}

	public abstract void setProperties(Map<String, Object> props);
}
