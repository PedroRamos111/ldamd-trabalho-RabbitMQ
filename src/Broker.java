import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;

class Broker implements Runnable {

	private static final String EXCHANGE_NAME = "topic_logs";
	private static Scanner entrada = new Scanner(System.in);

	public static void main(String[] args) throws IOException, TimeoutException {
		System.out.println("Qual o nome da sua corretora?");
		String corretora = entrada.nextLine();
		List<String> ativos = new ArrayList<>();
		menuAtivos(ativos);
		Thread threadRecv = new Thread(new Runnable() {
			public void run() {

				try {
					recebeMsg(ativos, "BOLSA");
				} catch (IOException | TimeoutException e) {
					e.printStackTrace();
				}
			}
		});
		Thread threadMenu = new Thread(new Runnable() {
			public void run() {

				try {
					menu(corretora);
				} catch (IOException | TimeoutException e) {
					e.printStackTrace();
				}
			}
		});

		threadRecv.start();
		threadMenu.start();

	}

	private static void menuAtivos(List<String> ativos) {
		String mais = "";
		int op;
		do {
			System.out.println("Você quer acompanhar " + mais + " alguma ação?");
			System.out.println("1 - Sim.");
			System.out.println("2 - Não.");

			op = entrada.nextInt();
			switch (op) {
				case 1:
					entrada.nextLine();
					mais = "mais";
					System.out.println("Qual açao você quer acompanhar?(Sigla)");
					String acao = entrada.nextLine();
					ativos.add(acao);
					break;
				case 2:
					entrada.nextLine();
					break;

				default:
					entrada.nextLine();
					System.out.println("Esse não é um valor valido");
					break;
			}
		} while (op != 2);
	}

	public static void menu(String corretora) throws IOException, TimeoutException {
		int op;
		do {
			System.out.println("Selecione uma opção:");
			System.out.println("1 - Comprar ações");
			System.out.println("2 - Vender ações");
			System.out.println("0 - Sair");

			op = entrada.nextInt();
			switch (op) {
				case 1:
					entrada.nextLine();
					System.out.println("Qual açao você quer comprar?(Sigla)");
					String acao = entrada.nextLine();
					System.out.println("Quantos desse ativo você quer comprar?");
					int quant = entrada.nextInt();
					System.out.println("Qual valor você pretende pagar neste ativo?");
					double valor = entrada.nextDouble();
					compra(corretora, acao, quant, valor);
					break;

				case 2:
					entrada.nextLine();
					System.out.println("Qual açao você quer vender?(Sigla)");
					acao = entrada.nextLine();
					System.out.println("Quantos desse ativo você quer vender?");
					quant = entrada.nextInt();
					System.out.println("Qual valor você pretende vender este ativo por?");
					valor = entrada.nextDouble();
					venda(corretora, acao, quant, valor);
					break;
				case 0:
					entrada.nextLine();
					break;
				default:
					entrada.nextLine();
					System.out.println("Esse não é um valor valido");
					break;
			}
		} while (op != 0);
	}

	@Override
	public void run() {
		System.out.println(Thread.currentThread().getState());

		System.out.println("Programa em execucao...");

	}

	public static void compra(String corretora, String ativo, int quant, double val)
			throws IOException, TimeoutException {
		String topic = "compra." + ativo;
		String message = quant + ";" + val + ";" + corretora;
		enviaPedido(topic, message);
	}

	public static void venda(String corretora, String ativo, int quant, double val)
			throws IOException, TimeoutException {
		String topic = "venda." + ativo;
		String message = quant + ";" + val + ";" + corretora;
		enviaPedido(topic, message);
	}

	public static void enviaPedido(String topic, String message) throws IOException, TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("gull.rmq.cloudamqp.com");
		factory.setUsername("zwzsdwdx");
		factory.setPassword("dIPnl1KCfla3vDb6FzjDOLh30BP-mrtu");
		factory.setVirtualHost("zwzsdwdx");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);

		channel.basicPublish(EXCHANGE_NAME, topic, null, message.getBytes("UTF-8"));
		System.out.println(" [x] Sent '" + topic + "':'" + message + "'");

		channel.close();
		connection.close();
	}

	public static void recebeMsg(List<String> ativos, String name) throws IOException, TimeoutException {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("gull.rmq.cloudamqp.com");
		factory.setUsername("zwzsdwdx");
		factory.setPassword("dIPnl1KCfla3vDb6FzjDOLh30BP-mrtu");
		factory.setVirtualHost("zwzsdwdx");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.exchangeDeclare(name, BuiltinExchangeType.TOPIC);
		String queueName = channel.queueDeclare().getQueue();

		for (int i = 0; i < ativos.size(); i++) {
			channel.queueBind(queueName, name, "#." + ativos.get(i));
		}

		DeliverCallback deliverCallback = (consumerTag, delivery) -> {

			String message = new String(delivery.getBody(), "UTF-8");
			String routingKey = delivery.getEnvelope().getRoutingKey();
			String[] dadosK = routingKey.split("\\.");
			String tipo = dadosK[0];
			String acao = dadosK[1];
			String[] dadosM = message.split(";");
			String quantidade = dadosM[0];
			String preco = dadosM[1];
			String corretora = dadosM[2];
			System.out.println(formatMsg(tipo, acao, quantidade, preco, corretora));

		};
		channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
		});

	}

	public static String formatMsg(String tipo, String acao, String quantidade, String preco, String corretora) {

		return "Novos pedidos sobre a ação " + acao + ":\nTipo: " + tipo + "\nQuantidade: " + quantidade + "\nPreço: "
				+ preco + "\nBroker responsavel: " + corretora;

	}
}
