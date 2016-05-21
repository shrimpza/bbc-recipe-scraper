package net.shrimpworks.bbc.recipes.chefs;

import java.io.IOException;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import net.shrimpworks.bbc.recipes.ScraperTask;

public class ChefIndex implements ScraperTask {

	private static final Stream<Character> ALPHABET = "abcdefghijklmnopqrstuvwxyz".chars().mapToObj(i -> (char)i);
	private static final String INDEX_URL = "/chefs/by/letters/%s";
	private static final Pattern PATTERN = Pattern.compile("/food/chefs/([a-z0-9_]+)");

	private final String dataPath;
	private final String rootUrl;
	private final Queue<ScraperTask> taskQueue;

	private final String url;

	public ChefIndex(String dataPath, String rootUrl, Queue<ScraperTask> taskQueue) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;

		this.taskQueue = taskQueue;

		this.url = rootUrl + String.format(INDEX_URL, ALPHABET.map(Object::toString).collect(Collectors.joining(",")));
	}

	@Override
	public void execute(Connection connection) {
		try {
			Document doc = connection.url(url).get();
			Elements chefs = doc.select("#chefs-by-letter ol.grid-view li a");

			chefs.parallelStream()
				 .map(e -> e.attr("href"))
				 .map(PATTERN::matcher)
				 .filter(Matcher::matches)
				 .map(m -> m.group(1))
				 .forEach(c -> taskQueue.add(new ChefSearch(dataPath, rootUrl, c, 1, taskQueue)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
