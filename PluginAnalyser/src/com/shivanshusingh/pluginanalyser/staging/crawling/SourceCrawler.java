package com.shivanshusingh.pluginanalyser.staging.crawling;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

/**
 * 
 * interface for the SourceCrawler: any crawler that crawls some source and
 * returns a {@link List} of eclipse update sites
 * 
 * @author Shivanshu Singh
 * 
 */
public interface SourceCrawler {

	/**
	 * returns a {@link List} of update sites fetched by
	 * crawling the source
	 * 
	 * @return {@link List}
	 */
	public Set getSites();

	/**
	 * crawls the source to  create or  refresh the siteURLs List. call getSites() after this to get the  updated Sites List.
	 * @see SourceCrawler.getSites()
	 */
	public void crawl();

}
