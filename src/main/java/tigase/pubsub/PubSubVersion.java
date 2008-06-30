package tigase.pubsub;


public class PubSubVersion {
	private PubSubVersion() {
	}

	public static String getVersion() {
		String version = PubSubVersion.class.getPackage().getImplementationVersion();
		return version == null ? "0.0.0" : version;
	}
}
