package tigase.component.eventbus;

public abstract class EventBus {

	public abstract <H extends EventHandler> void addHandler(EventType<H> type, H handler);

	public abstract <H extends EventHandler> void addListener(EventListener listener);

	public abstract <H extends EventHandler> void addListener(EventType<H> type, EventListener listener);

	public abstract void fire(Event<?> e);

	public abstract void fire(Event<?> e, Object source);

	public abstract <H extends EventHandler> void remove(EventType<H> type, H handler);

	public abstract <H extends EventHandler> void remove(H handler);

	protected void setEventSource(Event<EventHandler> event, Object source) {
		event.setSource(source);
	}
}
