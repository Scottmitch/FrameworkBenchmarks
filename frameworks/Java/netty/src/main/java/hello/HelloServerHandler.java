package hello;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.io.OutputStream;
import java.util.Date;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.SERVER;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.concurrent.TimeUnit.SECONDS;

@ChannelHandler.Sharable
public class HelloServerHandler extends ChannelInboundHandlerAdapter {
	static final HelloServerHandler INSTANCE = new HelloServerHandler();

	private static volatile CharSequence date = new AsciiString(DateFormatter.format(new Date()));
	private static final byte[] STATIC_PLAINTEXT = "Hello, World!".getBytes(CharsetUtil.UTF_8);
	private static final CharSequence PLAINTEXT_CLHEADER_VALUE = AsciiString.cached(String.valueOf(STATIC_PLAINTEXT.length));
	private static final CharSequence SERVER_NAME = AsciiString.cached("Netty");
	private static final HashedWheelTimer TIMER = new HashedWheelTimer(new DefaultThreadFactory("timer", true), 1, SECONDS);
	private static final ObjectMapper MAPPER;
	private static final int JSON_PAYLOAD_LENGTH;
	private static final CharSequence JSON_CLHEADER_VALUE;

	static {
		MAPPER = new ObjectMapper();
		MAPPER.registerModule(new AfterburnerModule());
		try {
			JSON_PAYLOAD_LENGTH = MAPPER.writeValueAsBytes(newMsg()).length;
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		JSON_CLHEADER_VALUE = new AsciiString(String.valueOf(JSON_PAYLOAD_LENGTH));

		TIMER.start();
		TIMER.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				date = new AsciiString(DateFormatter.format(new Date()));
				TIMER.newTimeout(this, 1, SECONDS);
			}
		}, 1, SECONDS);
	}

	private HelloServerHandler() {
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		try {
			if (msg instanceof HttpRequest) {
				String uri = ((HttpRequest) msg).uri();
				if (uri.equals("/plaintext")) {
					writePlainResponse(ctx, ctx.alloc().ioBuffer(STATIC_PLAINTEXT.length).writeBytes(STATIC_PLAINTEXT));
				} else if (uri.equals("/json")) {
					ByteBuf outputBuffer = ctx.alloc().ioBuffer(JSON_PAYLOAD_LENGTH);
					MAPPER.writeValue((OutputStream) new ByteBufOutputStream(outputBuffer), newMsg());
					writeJsonResponse(ctx, outputBuffer);
				} else {
					ctx.write(new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, Unpooled.EMPTY_BUFFER,
								EmptyHttpHeaders.INSTANCE, EmptyHttpHeaders.INSTANCE))
							.addListener(ChannelFutureListener.CLOSE);
				}
			}
		} finally {
			ReferenceCountUtil.release(msg);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		ctx.close();
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}

	private static final class Message {
		public final String message;

		public Message(String message) {
			this.message = message;
		}
	}

	private static Message newMsg() {
		return new Message("Hello, World!");
	}

	private static void writePlainResponse(ChannelHandlerContext ctx, ByteBuf buf) {
		ctx.write(makeResponse(buf, TEXT_PLAIN, PLAINTEXT_CLHEADER_VALUE), ctx.voidPromise());
	}

	private static void writeJsonResponse(ChannelHandlerContext ctx, ByteBuf buf) {
		ctx.write(makeResponse(buf, APPLICATION_JSON, JSON_CLHEADER_VALUE), ctx.voidPromise());
	}

	private static FullHttpResponse makeResponse(ByteBuf buf, CharSequence contentType, CharSequence contentLength) {
		return new DefaultFullHttpResponse(HTTP_1_1, OK, buf,
				new ReadOnlyHttpHeaders(false, CONTENT_TYPE, contentType, SERVER, SERVER_NAME,
										DATE, date, CONTENT_LENGTH, contentLength),
				EmptyHttpHeaders.INSTANCE);
	}
}
