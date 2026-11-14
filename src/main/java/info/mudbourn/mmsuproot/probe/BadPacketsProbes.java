package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bad Packets' play channels in both directions over the integrated server's connection. Its channels must exist before the
 * connection is made, so {@link #register()} runs when the client starts; each probe then sends one payload and waits for the receiver.
 */
final class BadPacketsProbes {
	private static final String API = "lol.bai.badpackets.api.";
	private static final CustomPacketPayload.Type<Ping> S2C = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("uproot_probe", "to_client"));
	private static final CustomPacketPayload.Type<Ping> C2S = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("uproot_probe", "to_server"));
	private static final StreamCodec<RegistryFriendlyByteBuf, Ping> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Ping::text, Ping::new);
	private static final AtomicReference<String> AT_CLIENT = new AtomicReference<>();
	private static final AtomicReference<String> AT_SERVER = new AtomicReference<>();

	private BadPacketsProbes() { }

	private record Ping(String text) implements CustomPacketPayload {
		@Override
		public Type<? extends CustomPacketPayload> type() {
			return text.startsWith("c2s") ? C2S : S2C;
		}
	}

	private static ClassLoader loader() {
		return BadPacketsProbes.class.getClassLoader();
	}

	/** A badpackets PacketReceiver that stores the text of the payload it gets. */
	private static Object receiver(AtomicReference<String> into) throws ClassNotFoundException {
		InvocationHandler handler = (proxy, method, args) -> {
			if (method.getName().equals("receive")) into.set(((Ping) args[1]).text());
			return null;
		};
		return Proxy.newProxyInstance(loader(), new Class<?>[] {Class.forName(API + "PacketReceiver", true, loader())}, handler);
	}

	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("badpackets")) return;
		try {
			Class<?> play = Class.forName(API + "play.PlayPackets", true, loader());
			Class<?> receiverType = Class.forName(API + "PacketReceiver", true, loader());
			play.getMethod("registerClientChannel", CustomPacketPayload.Type.class, StreamCodec.class).invoke(null, S2C, CODEC);
			play.getMethod("registerClientReceiver", CustomPacketPayload.Type.class, receiverType).invoke(null, S2C, receiver(AT_CLIENT));
			play.getMethod("registerServerChannel", CustomPacketPayload.Type.class, StreamCodec.class).invoke(null, C2S, CODEC);
			play.getMethod("registerServerReceiver", CustomPacketPayload.Type.class, receiverType).invoke(null, C2S, receiver(AT_SERVER));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not register the Bad Packets probe channels", e);
		}
	}

	static List<Probe> all() {
		return List.of(
			Probe.steps("badpackets.server-to-client-payload", 200, BadPacketsProbes::toClient),
			Probe.steps("badpackets.client-to-server-payload", 200, BadPacketsProbes::toServer));
	}

	private static String toClient(Probe.Ctx c, int tick) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("badpackets")) throw new Probe.Skip("Bad Packets is not installed");
		if (tick == 1) {
			Class<?> sender = Class.forName(API + "PacketSender", true, loader());
			Object context = c.onServer(() -> sender.getMethod("s2c", ServerPlayer.class).invoke(null, c.serverPlayer()));
			Method send = sender.getMethod("send", CustomPacketPayload.class);
			c.onServer(() -> send.invoke(context, new Ping("s2c hello")));
			return null;
		}
		return "s2c hello".equals(AT_CLIENT.get()) ? "the client receiver got the payload the server sent through PacketSender.s2c" : null;
	}

	private static String toServer(Probe.Ctx c, int tick) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("badpackets")) throw new Probe.Skip("Bad Packets is not installed");
		if (tick == 1) {
			Class<?> sender = Class.forName(API + "PacketSender", true, loader());
			Object context = sender.getMethod("c2s").invoke(null);
			sender.getMethod("send", CustomPacketPayload.class).invoke(context, new Ping("c2s hello"));
			return null;
		}
		return "c2s hello".equals(AT_SERVER.get()) ? "the server receiver got the payload the client sent through PacketSender.c2s" : null;
	}
}
