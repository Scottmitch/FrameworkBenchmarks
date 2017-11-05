package hello;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueChannelOption;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;

import java.net.InetSocketAddress;

public class HelloWebServer {

	static {
		ResourceLeakDetector.setLevel(Level.DISABLED);
	}

	private final int port;

	public HelloWebServer(int port) {
		this.port = port;
	}

	public void run() throws Exception {
		// Configure the server.
		ServerBootstrap b = new ServerBootstrap();
		final EventLoopGroup group;
		final Class<? extends ServerChannel> serverChannelClass;
		if (Epoll.isAvailable()) {
			b.option(EpollChannelOption.SO_REUSEPORT, true)
					.option(EpollChannelOption.TCP_FASTOPEN, 3);
			serverChannelClass = EpollServerSocketChannel.class;
			group = new EpollEventLoopGroup();
		} else if (KQueue.isAvailable()) {
			b.option(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS, true)
				.childOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS, true);
			serverChannelClass = KQueueServerSocketChannel.class;
			group = new KQueueEventLoopGroup();
		} else {
			serverChannelClass = NioServerSocketChannel.class;
			group = new NioEventLoopGroup();
		}
		try {
			InetSocketAddress inet = new InetSocketAddress(port);

			b.group(group)
					.channel(serverChannelClass)
					.childHandler(HelloServerInitializer.INSTANCE)
					.option(ChannelOption.SO_BACKLOG, 4096)
					.option(ChannelOption.SO_REUSEADDR, true)
					.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
					.option(ChannelOption.MAX_MESSAGES_PER_READ, 128)
					.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
					.childOption(ChannelOption.MAX_MESSAGES_PER_READ, 64)
					.childOption(ChannelOption.WRITE_SPIN_COUNT, 64)
					.childOption(ChannelOption.SO_LINGER, 0)
					.childOption(ChannelOption.TCP_NODELAY, true);

			Channel ch = b.bind(inet).sync().channel();

			System.out.printf("Httpd started. Listening on: %s%n", inet.toString());

			ch.closeFuture().sync();
		} finally {
			group.shutdownGracefully().sync();
		}
	}

	public static void main(String[] args) throws Exception {
		int port;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		} else {
			port = 8080;
		}
		new HelloWebServer(port).run();
	}
}
