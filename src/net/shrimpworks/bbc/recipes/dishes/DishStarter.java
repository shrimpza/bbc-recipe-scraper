package net.shrimpworks.bbc.recipes.dishes;

import java.util.Queue;
import java.util.stream.Stream;

import org.jsoup.Connection;

import net.shrimpworks.bbc.recipes.ScraperTask;

public class DishStarter implements ScraperTask {

	private static final Stream<Character> ALPHABET = "abcdefghijklmnopqrstuvwxyz".chars().mapToObj(i -> (char)i);

	private final String dataPath;
	private final String rootUrl;
	private final Queue<ScraperTask> taskQueue;

	public DishStarter(String dataPath, String rootUrl, Queue<ScraperTask> taskQueue) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;

		this.taskQueue = taskQueue;
	}

	@Override
	public void execute(Connection connection) {
		ALPHABET.parallel()
				.forEach(l -> taskQueue.add(new DishIndex(dataPath, rootUrl, l, taskQueue)));
	}
}
