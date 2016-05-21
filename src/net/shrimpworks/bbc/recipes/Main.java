package net.shrimpworks.bbc.recipes;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import net.shrimpworks.bbc.recipes.chefs.ChefIndex;
import net.shrimpworks.bbc.recipes.dishes.DishStarter;

public class Main {

	private static final String ROOT = "http://www.bbc.co.uk/food";

	private static final int THREADS = 2; // increasing this beyond 1 seems to introduce odd behaviour within JSoup

	public static void main(String... args) throws IOException, InterruptedException {
		final String dataPath = args[0];

		final Connection connection = Jsoup.connect(ROOT);
		final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

		final BlockingQueue<ScraperTask> tasks = new LinkedBlockingQueue<>();
		tasks.add(new ChefIndex(dataPath, ROOT, tasks));
		tasks.add(new DishStarter(dataPath, ROOT, tasks));

		int taskCount = 0;
		ScraperTask task;
		while ((task = tasks.take()) != null) {
			final ScraperTask lolTask = task;
			executor.submit(() -> lolTask.execute(connection));
			taskCount++;

			if (taskCount % 10 == 0) System.out.print(".");
		}

		// since this is a hax job, for now we never know when it's completed, so you'll just have to terminate it when you think it's done (the executor keeps the main thread active)
	}

}
