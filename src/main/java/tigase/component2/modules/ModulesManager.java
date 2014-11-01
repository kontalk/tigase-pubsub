package tigase.component2.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component2.PacketWriter;
import tigase.component2.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;

public class ModulesManager {

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final ArrayList<Module> modules = new ArrayList<Module>();

	private final PacketWriter writer;

	public ModulesManager() {
		this.writer = null;
	}

	public ModulesManager(PacketWriter writer) {
		this.writer = writer;
	}

	@SuppressWarnings("unchecked")
	protected <T extends Module> T getByClass(final Class<T> moduleClass) {
		for (Module m : modules) {
			if (moduleClass.isInstance(m)) {
				return (T) m;
			}
		}
		return null;
	}

	public Collection<String> getFeatures() {
		final ArrayList<String> features = new ArrayList<String>();
		for (Module m : modules) {
			String[] fs = m.getFeatures();
			if (fs != null) {
				for (String string : fs) {
					features.add(string);
				}
			}
		}
		return features;
	}

	public boolean isRegistered(final Class<? extends Module> moduleClass) {
		for (Module m : modules) {
			if (moduleClass.isInstance(m)) {
				return true;
			}
		}
		return false;
	}

	public boolean isRegistered(final Module module) {
		return this.modules.contains(module);
	}

	public boolean process(final Packet packet) throws ComponentException, TigaseStringprepException {
		return process(packet, this.writer);
	}

	public boolean process(final Packet packet, final PacketWriter writer) throws ComponentException, TigaseStringprepException {
		if (writer == null)
			throw new Error("ElementWriter is null");
		boolean handled = false;
		if (log.isLoggable(Level.FINER)) {
			log.finest("Processing packet: " + packet.toString());
		}

		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();
			if (criteria != null && criteria.match(packet.getElement())) {
				handled = true;
				if (log.isLoggable(Level.FINER)) {
					log.finer("Handled by module " + module.getClass());
				}
				module.process(packet);
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Finished " + module.getClass());
				}
				break;
			}
		}
		return handled;
	}

	public <T extends Module> T register(final T module, boolean skipIfExists) {
		return register((Class<T>) module.getClass(), module, skipIfExists);
	}
	
	public <T extends Module, S extends T> T register(final Class<T> cls, final S module, boolean skipIfExists) {
		if (log.isLoggable(Level.CONFIG))
			log.config("Register Component module: " + module.getClass().getCanonicalName() + " as " + cls.getCanonicalName());

		if (skipIfExists) {
			@SuppressWarnings("unchecked")
			T old = getByClass(cls);
			if (old != null)
				return old;
		}

		this.modules.add(module);

		if (module instanceof InitializingModule) {
			((InitializingModule) module).onRegisterModule();
		}

		return module;
	}

	public void reset() {
		this.modules.clear();
	}

	public void unregister(final Class<? extends Module> moduleClass) {
		final Set<Module> toRemove = new HashSet<Module>();
		for (Module m : modules) {
			if (moduleClass.isInstance(m)) {
				toRemove.add(m);
			}
		}
		for (Module module : toRemove) {
			unregister(module);
		}

	}

	public void unregister(final Module module) {
		if (log.isLoggable(Level.CONFIG))
			log.config("Unregister Component module: " + module.getClass().getCanonicalName());
		this.modules.remove(module);

		if (module instanceof InitializingModule) {
			((InitializingModule) module).onUnegisterModule();
		}

	}

}
