# BBC Food Recipe Scraper

A simple HTML page scraper which finds and stores recipes from the BBC Food
website as JSON files. Images are also downloaded where available.

## Build

Apache Ant is required to build this project.

From the project root directory, execute:

`$ ant`

## Usage

Execute:

`$ java -jar bbc-recipe-scraper.jar <output-path>`

All recipes found and scraped successfully will placed into the `<output-path>`
provided, and all images will be placed into `<output-path>/images`.
