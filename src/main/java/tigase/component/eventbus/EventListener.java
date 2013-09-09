package tigase.component.eventbus;

public interface EventListener extends EventHandler {

	void onEvent(Event<? extends EventHandler> event);

}
