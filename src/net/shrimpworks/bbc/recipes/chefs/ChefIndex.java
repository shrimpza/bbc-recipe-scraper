package net.shrimpworks.bbc.recipes.chefs;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import net.shrimpworks.bbc.recipes.ScraperTask;

public class ChefIndex implements ScraperTask {

	private static final Stream<Character> ALPHABET = "abcdefghijklmnopqrstuvwxyz".chars().mapToObj(i -> (char)i);
	private static final String INDEX_URL = "/chefs/by/letters/%s";
	private static final Pattern PATTERN = Pattern.compile("/food/chefs/([a-z0-9_]+)");

	private final String dataPath;
	private final String rootUrl;
	private final ExecutorService executor;

	private final String url;

	public ChefIndex(String dataPath, String rootUrl, ExecutorService executor) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;

		this.executor = executor;

		this.url = rootUrl + String.format(INDEX_URL, ALPHABET.map(Object::toString).collect(Collectors.joining(",")));
	}

	@Override
	public void execute(Connection connection) {
		try {
			System.out.println(url);
			Document doc = connection.url(url).get();
			Elements chefs = doc.select("#chefs-by-letter ol.grid-view li a");

			chefs.parallelStream()
				 .map(e -> e.attr("href"))
				 .map(PATTERN::matcher)
				 .filter(Matcher::matches)
				 .map(m -> m.group(1))
				 .forEach(c -> executor.submit(() -> new ChefSearch(dataPath, rootUrl, c, 1, executor).execute(Jsoup.connect(rootUrl)))); // TODO REVIEW makes a new connection per search, which will be reused by recipe lookups found by this search - is this improving the thread-related issues in jsoup?
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
