package net.shrimpworks.bbc.recipes;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import net.shrimpworks.bbc.recipes.chefs.ChefIndex;

public class Main {

	private static final String ROOT = "http://www.bbc.co.uk/food";

	private static final int THREADS = 2;

	public static void main(String... args) throws IOException {
		final String dataPath = args[0];

		Connection connection = Jsoup.connect(ROOT);

		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		executor.submit(() -> new ChefIndex(dataPath, ROOT, executor).execute(connection));
	}

}
