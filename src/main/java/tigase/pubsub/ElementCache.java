package tigase.pubsub;

public class ElementCache<E> {

	private E data;

	private final long maxTimeToLive;

	private long readTimestamp;

	private long updateTimestamp;

	public ElementCache(final long maxTimeToLive) {
		this.maxTimeToLive = maxTimeToLive;
	}

	public E getData() {
		long now = System.currentTimeMillis();
		if (updateTimestamp + maxTimeToLive <= now) {
			data = null;
		} else {
			this.readTimestamp = System.currentTimeMillis();
		}
		return data;
	}

	public void setData(E data) {
		this.data = data;
		this.updateTimestamp = System.currentTimeMillis();
		this.readTimestamp = this.updateTimestamp;
	}

}
