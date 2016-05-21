package net.shrimpworks.bbc.recipes.dishes;

import java.io.IOException;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.shrimpworks.bbc.recipes.ScraperTask;

public class DishIndex implements ScraperTask {

	private static final String INDEX_URL = "/dishes/by/letter/%s";

	private static final Pattern PATTERN = Pattern.compile("([a-z0-9_]+)");

	private final String dataPath;

	private final String rootUrl;

	private final Queue<ScraperTask> taskQueue;

	private final String url;

	public DishIndex(String dataPath, String rootUrl, char letter, Queue<ScraperTask> taskQueue) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;

		this.taskQueue = taskQueue;

		this.url = rootUrl + String.format(INDEX_URL, letter);
	}

	@Override
	public void execute(Connection connection) {
		try {
			Document doc = connection.url(url).get();
			Elements dishes = doc.select("#foods-by-letter ol.grid-view li");

			dishes.parallelStream()
				  .map(Element::id)
				  .map(PATTERN::matcher)
				  .filter(Matcher::matches)
				  .map(Matcher::group)
				  .forEach(d -> taskQueue.add(new DishSearch(dataPath, rootUrl, d, 1, taskQueue)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
