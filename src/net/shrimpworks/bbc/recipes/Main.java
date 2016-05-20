package net.shrimpworks.bbc.recipes;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.shrimpworks.bbc.recipes.chefs.ChefIndex;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

public class Main {

	private static final String ROOT = "http://www.bbc.co.uk/food";

	private static final int THREADS = 1; // increasing this beyond 1 seems to introduce odd behaviour within JSoup

	public static void main(String... args) throws IOException {
		final String dataPath = args[0];

		Connection connection = Jsoup.connect(ROOT);

		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		executor.submit(() -> new ChefIndex(dataPath, ROOT, executor).execute(connection));

		// since this is a hax job, for now we never know when it's completed, so you'll just have to terminate it when you think it's done (the executor keeps the main thread active)
	}

}
