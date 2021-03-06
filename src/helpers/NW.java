package helpers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.CleanableURL;

public class NW {
	
	public static String expandComplete(String domainName, String href) {
		if (href.toLowerCase().startsWith("//")) {
			return addProtcol(href);
		}
		if (href.toLowerCase().startsWith("http")) {
			return href;
		}
		if(href.startsWith("/")) {
			href=href.substring(1);
		}
		return domainName + (domainName.endsWith("/") ? "" : "/") + href;
	}
	
	// adds protocol to URL if missing
	public static String addProtcol(String url) {
		if (url.toLowerCase().startsWith("//")) {
			url = "https:" + url;
		}
		if (!url.toLowerCase().startsWith("http")) {
			url = "https://" + url;
		}
		return url;
	}

	public static String removePath(String baseURL) {
		String protocol = getProtocol(baseURL);
		if(protocol.equals("")) {
			return baseURL.contains("/") ? baseURL.substring(0, baseURL.indexOf("/")) : baseURL;
		}else {
			String urlNoProtocol = baseURL.substring(protocol.length());
			urlNoProtocol = urlNoProtocol.contains("/") ? urlNoProtocol.substring(0, urlNoProtocol.indexOf("/")) : urlNoProtocol;
			return protocol+urlNoProtocol;
		}
	}
	
	public static boolean isExternal(String baseURL, CleanableURL gr, boolean strict, ArrayList<String> secondLevelDomains) {
		baseURL = NW.removePath(baseURL);
		String checkURL = gr.getSt();
		if (checkURL.startsWith("/")) {
			return false;
		}
		if (gr.wasCleaned()) {
			boolean sameURL;
			if(strict) {
				sameURL = checkURL.startsWith(baseURL);				
			}else {
				String checkURLNoSub = removeSubdomains(checkURL,secondLevelDomains);
				String baseURLNoSub = removeSubdomains(baseURL,secondLevelDomains);
				
				sameURL = checkURLNoSub.contains(baseURLNoSub);
				if(!sameURL) {
					sameURL = baseURLNoSub.contains(NW.removePath(checkURLNoSub));
				}
			}
			return !sameURL;
		}
		return false;
	}
	
	public static ArrayList<String> getPublicSuffixList(boolean loadFromPublicSufficOrg, Proxy proxy, boolean debug, String userAgent) {
		ArrayList<String> secondLevelDomains = new ArrayList<String>();
		if(loadFromPublicSufficOrg) {
			try {
				String a = NW.getHTTP("https://publicsuffix.org/list/public_suffix_list.dat", false, true, proxy, userAgent, null);
				Scanner scanner = new Scanner(a);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					if(!line.startsWith("//") && !line.startsWith("*") && line.contains(".")) {
						secondLevelDomains.add(line);
					}
				}
				scanner.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(debug) {
				System.out.println("-- Loaded "+secondLevelDomains.size()+" second level domains");
			}
		}else {			
			secondLevelDomains.add("co.uk");secondLevelDomains.add("co.at");secondLevelDomains.add("or.at");secondLevelDomains.add("ac.at");secondLevelDomains.add("gv.at");secondLevelDomains.add("ac.at");secondLevelDomains.add("ac.uk");secondLevelDomains.add("gov.uk");secondLevelDomains.add("ltd.uk");secondLevelDomains.add("fed.us");secondLevelDomains.add("isa.us");secondLevelDomains.add("nsn.us");secondLevelDomains.add("dni.us");secondLevelDomains.add("ac.ru");secondLevelDomains.add("com.ru");secondLevelDomains.add("edu.ru");secondLevelDomains.add("gov.ru");secondLevelDomains.add("int.ru");secondLevelDomains.add("mil.ru");secondLevelDomains.add("net.ru");secondLevelDomains.add("org.ru");secondLevelDomains.add("pp.ru");secondLevelDomains.add("com.au");secondLevelDomains.add("net.au");secondLevelDomains.add("org.au");secondLevelDomains.add("edu.au");secondLevelDomains.add("gov.au");
		}
		return secondLevelDomains;
	}
	
	public static String getProtocol(String url) {
		String containsProtocolPattern = "^([a-zA-Z]*:\\/\\/)|^(\\/\\/)";
		Pattern pattern = Pattern.compile(containsProtocolPattern);
		Matcher m = pattern.matcher(url);
	    if (m.find()) {	      
	    	return m.group();
	    }
	    return "";
	}
	
	public static String removeSubdomains(String url, ArrayList<String> secondLevelDomains) {
		// We need our URL in three parts, protocol - domain - path
		String protocol= getProtocol(url);		
		url = url.substring(protocol.length());
		String urlDomain=url;
		String path="";
		if(urlDomain.contains("/")) {
			int slashPos = urlDomain.indexOf("/");
			path=urlDomain.substring(slashPos);
			urlDomain=urlDomain.substring(0, slashPos);
		}
		// Done, now let us count the dots . . 
		int dotCount = Strng.countOccurrences(urlDomain, ".");
		// example.com <-- nothing to cut
		if(dotCount==1){
			return protocol+url;
		}
		int dotOffset=2; // subdomain.example.com <-- default case, we want to remove everything before the 2nd last dot
		// however, somebody had the glorious idea, to have second level domains, such as co.uk
		for (String secondLevelDomain : secondLevelDomains) {
			// we need to check if our domain ends with a second level domain
			// example: something.co.uk we don't want to cut away "something", since it isn't a subdomain, but the actual domain
			if(urlDomain.endsWith(secondLevelDomain)) {
				// we increase the dot offset with the amount of dots in the second level domain (co.uk = +1)
				dotOffset += Strng.countOccurrences(secondLevelDomain, ".");
				break;
			}
		}
		// if we have something.co.uk, we have a offset of 3, but only 2 dots, hence nothing to remove
		if(dotOffset>dotCount) {
			return protocol+urlDomain+path;
		}
		// if we have sub.something.co.uk, we have a offset of 3 and 3 dots, so we remove "sub"
		int pos = Strng.nthLastIndexOf(dotOffset, ".", urlDomain)+1;
		urlDomain = urlDomain.substring(pos);	
		return protocol+urlDomain+path;
	}

	public static String getHTTP(String url, boolean isJsonOutput, boolean isQuiet, Proxy proxy, String userAgent, String cookie) throws Exception {
		if (!isQuiet) {
			if (!isJsonOutput) {
				System.out.print("-- HTTP GET '" + url+"'");
			}
		}
			
		URL urlObj = new URL(url);
		HttpURLConnection conn;
		if(proxy!=null) {
			conn = (HttpURLConnection) urlObj.openConnection(proxy);			
		}else {
			conn = (HttpURLConnection) urlObj.openConnection();
		}
		conn.setReadTimeout(7000);
		if(cookie!=null) {
			conn.setRequestProperty("Cookie", cookie); 			
		}
        conn.setRequestProperty("User-Agent", userAgent);
		int status = 0;
		try {
			status = conn.getResponseCode();
			if (!isQuiet) {
				if (!isJsonOutput) {
					System.out.print(" -> got response code = " + status + "\r\n");
				}
			}
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
					String newUrl = conn.getHeaderField("Location");
					conn = (HttpURLConnection) new URL(newUrl).openConnection();
				}else {
					model.Error.errors.add("Error doing HTTP GET for '"+url+"': got response code "+status);
					return "";
				}
			}

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuffer html = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				html.append(inputLine+"\n");
			}
			in.close();
			return html.toString();
		} catch (javax.net.ssl.SSLHandshakeException e) {
			model.Error.errors.add("HTTP GET - SSLHandshake Exception occured for '"+url+"': "+e.getMessage());
		} catch (java.net.UnknownHostException e) {
			model.Error.errors.add("HTTP GET - Unknown host '"+url+"': "+e.getMessage());
		} catch (java.io.FileNotFoundException e) {
			model.Error.errors.add("HTTP GET - Could not find (404) for '"+url+"': "+e.getMessage());
		} catch (java.net.ConnectException e){
			model.Error.errors.add("HTTP GET - Connection failed for'"+url+"': "+e.getMessage());
		} catch (Exception e) {
			model.Error.errors.add("HTTP GET - Uncaught exception occured for '"+url+"': "+e.getMessage());
		}
		return "";
	}
}
