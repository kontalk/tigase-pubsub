package tigase.component2.eventbus;

public interface EventListener extends EventHandler {

	void onEvent(Event<? extends EventHandler> event);

}
