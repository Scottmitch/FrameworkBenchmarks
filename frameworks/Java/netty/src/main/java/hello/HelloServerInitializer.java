package hello;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

@ChannelHandler.Sharable
public final class HelloServerInitializer extends ChannelInitializer<SocketChannel> {
	static final HelloServerInitializer INSTANCE = new HelloServerInitializer();

	private HelloServerInitializer() {
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ch.pipeline()
                .addLast("encoder", new HttpResponseEncoder())
                .addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192, false, 256))
                .addLast("handler", HelloServerHandler.INSTANCE);
	}
}
