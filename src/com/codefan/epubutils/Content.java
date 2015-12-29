package com.codefan.epubutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.codefan.epubutils.BaseFindings.NavPoint;
import com.codefan.epubutils.BaseFindings.XmlItem;

public class Content {

	private ZipFile epubFile;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	private int playOrder = -1;

	public Content() {
		entryNames = new ArrayList<>();

		container = new Container();
		opfPackage = new Package();
		toc = new Toc();
	}

	// Debug
	public void print() throws IllegalArgumentException, IllegalAccessException {
		System.out.println("Printing zipEntryNames...\n");

		for (int i = 0; i < entryNames.size(); i++) {
			System.out.println("(" + i + ")" + entryNames.get(i));
		}

		getContainer().print();
		getPackage().print();
		getToc().print();
	}

	public BookSection getNextBookSection() throws IOException {
		String[] entryNameAndLabel = getEntryNameAndLabel(++this.playOrder);

		if (entryNameAndLabel != null) {
			return getBookSectionFromEntryNameAndLabel(entryNameAndLabel);
		}

		throw new IOException("Referenced file not found!" + " (playOrder: " + (this.playOrder - 1) + ")");
	}

	public BookSection getPrevBookSection() throws IOException {
		String[] entryNameAndLabel = getEntryNameAndLabel(--this.playOrder);

		if (entryNameAndLabel != null) {
			return getBookSectionFromEntryNameAndLabel(entryNameAndLabel);
		}

		throw new IOException("Referenced file not found!" + " (playOrder: " + (this.playOrder - 1) + ")");
	}

	public BookSection getBookSection(int index) throws IOException {
		String[] entryNameAndLabel = getEntryNameAndLabel(index);

		if (entryNameAndLabel != null) {
			return getBookSectionFromEntryNameAndLabel(entryNameAndLabel);
		}

		throw new IOException("Referenced file not found!" + " (playOrder: " + index + ")");
	}

	private String[] getEntryNameAndLabel(int index) throws IOException {

		if (getToc() != null) {
			List<NavPoint> navPoints = getToc().getNavMap().getNavPoints();

			NavPoint navPoint = navPoints.get(index);

			if (navPoint.getContentSrc() != null) {
				return new String[] { navPoint.getContentSrc(), navPoint.getNavLabel() };
			} else { // Find from id
				List<XmlItem> xmlItemList = getPackage().getManifest().getXmlItemList();
				for (int j = 0; j < xmlItemList.size(); j++) {
					Map<String, String> attributeMap = xmlItemList.get(j).getAttributes();

					String id = attributeMap.get("id");

					if (id.contains(navPoint.getId())) {
						return new String[] { attributeMap.get("href"), navPoint.getNavLabel() };
					}
				}
			}

		} else { // Try spine instead
			List<XmlItem> spineItemList = getPackage().getSpine().getXmlItemList();

			XmlItem spineItem = spineItemList.get(playOrder);

			String idRef = spineItem.getAttributes().get("idref");

			List<XmlItem> manifestItemList = getPackage().getManifest().getXmlItemList();

			for (int j = 0; j < manifestItemList.size(); j++) {
				Map<String, String> attributeMap = manifestItemList.get(j).getAttributes();

				String id = attributeMap.get("id");

				if (id.contains(idRef)) {
					return new String[] { attributeMap.get("href"), null };
				}
			}
		}

		return null;
	}

	private BookSection getBookSectionFromEntryNameAndLabel(String[] entryNameAndLabel) throws IOException {
		BookSection bookSection = new BookSection();

		String href = entryNameAndLabel[0];
		String label = entryNameAndLabel[1];

		for (int i = 0; i < getEntryNames().size(); i++) {
			String entryName = getEntryNames().get(i);

			int lastSlashIndex = entryName.lastIndexOf("/");
			String fileName = entryName.substring(lastSlashIndex + 1);

			if (href.contains(fileName)) { // href actually exists.
				ZipEntry zipEntry = epubFile.getEntry(entryName);
				InputStream zipEntryInputStream = epubFile.getInputStream(zipEntry);

				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipEntryInputStream));
				StringBuilder fileContent = new StringBuilder();

				String line;
				while ((line = bufferedReader.readLine()) != null) {
					fileContent.append(line);
				}

				String fileContentStr = replaceLinkedContentWithCss(fileContent.toString());

				String extension = null;

				int dotIndex = fileName.lastIndexOf('.');
				if (dotIndex != -1) {
					extension = fileName.substring(0, dotIndex);
				}

				bookSection.setSectionContent(fileContentStr);
				bookSection.setExtension(extension);
				bookSection.setLabel(label);

				// epubFile.close();
				bufferedReader.close();

				return bookSection;
			}
		}

		throw new IOException("Referenced file not found!");
	}

	private String[] getCssHrefAndLinkPart(String htmlContent) {
		int indexOfLinkStart = htmlContent.indexOf("<link");

		if (indexOfLinkStart != -1) {
			int indexOfLinkEnd = htmlContent.indexOf("/>", indexOfLinkStart);

			String linkStr = htmlContent.substring(indexOfLinkStart, indexOfLinkEnd + 2);

			int indexOfHrefStart = linkStr.indexOf("href=\"");
			int indexOfHrefEnd = linkStr.indexOf("\"", indexOfHrefStart + 6);

			String cssHref = linkStr.substring(indexOfHrefStart + 6, indexOfHrefEnd);

			if (cssHref.endsWith(".css")) {
				return new String[]{ cssHref, linkStr };
			}
		}

		return null;
	}

	private String replaceLinkedContentWithCss(String htmlContent) throws IOException {

		// <link rel="stylesheet" type="text/css" href="docbook-epub.css"/>

		String[] cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);

		while (cssHrefAndLinkPart != null) {
			
			String cssHref = cssHrefAndLinkPart[0];
			String linkPart = cssHrefAndLinkPart[1];
			
			for (int i = 0; i < getEntryNames().size(); i++) {
				String entryName = getEntryNames().get(i);

				int lastSlashIndex = entryName.lastIndexOf("/");
				String fileName = entryName.substring(lastSlashIndex + 1);

				if (cssHref.contains(fileName)) { // css exists.
					ZipEntry zipEntry = epubFile.getEntry(entryName);
					InputStream zipEntryInputStream = epubFile.getInputStream(zipEntry);

					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipEntryInputStream));
					StringBuilder fileContent = new StringBuilder();

					fileContent.append("<style type=\"text/css\">");

					String line;
					while ((line = bufferedReader.readLine()) != null) {
						fileContent.append(line);
					}

					bufferedReader.close();

					fileContent.append("</style>");

					htmlContent = htmlContent.replace(linkPart, fileContent.toString());

					cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);
					break;
				}
			}
		}

		return htmlContent;
	}

	public List<String> getEntryNames() {
		return entryNames;
	}

	public void addEntryName(String zipEntryName) {
		entryNames.add(zipEntryName);
	}

	public Container getContainer() {
		return container;
	}

	public Package getPackage() {
		return opfPackage;
	}

	public Toc getToc() {
		return toc;
	}

	public ZipFile getEpubFile() {
		return epubFile;
	}

	public void setEpubFile(ZipFile epubFile) {
		this.epubFile = epubFile;
	}

}