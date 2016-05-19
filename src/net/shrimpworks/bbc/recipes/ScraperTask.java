package net.shrimpworks.bbc.recipes;

import org.jsoup.Connection;

public interface ScraperTask {

	void execute(Connection connection);
}
