/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.tags.file;

import java.util.ArrayList;
import java.util.List;

import org.opentdc.service.LocalizedTextModel;
import org.opentdc.tags.TagModel;
import org.opentdc.util.LanguageCode;

/**
 * @author Bruno Kaiser
 *
 */
public class TextedTag {
	private TagModel model;
	ArrayList<LocalizedTextModel> localizedTexts;
	
	/**
	 * Constructor.
	 */
	public TextedTag() {
		localizedTexts = new ArrayList<LocalizedTextModel>();
	}
	
	/**
	 * Retrieve the TagsModel.
	 * @return the model
	 */
	public TagModel getModel() {
		return model;
	}
	
	/**
	 * Set the TagsModel.
	 * @param model
	 */
	public void setModel(TagModel model) {
		this.model = model;
	}
	
	/**
	 * Retrieve a list of all localized texts.
	 * @return the list of localized texts
	 */
	public List<LocalizedTextModel> getLocalizedTexts() {
		return localizedTexts;
	}
	
	/**
	 * Set a list of localized texts.
	 * @param localizedTexts
	 */
	public void setLocalizedTexts(ArrayList<LocalizedTextModel> localizedTexts) {
		this.localizedTexts = localizedTexts;
	}
	
	/**
	 * Add a localized text to a tag.
	 * @param text
	 */
	public void addText(LocalizedTextModel text) {
		this.localizedTexts.add(text);
	}
	
	/**
	 * Remove a localized text from a tag.
	 * @param text the localized text
	 * @return true if the removal was successful
	 */
	public boolean removeText(LocalizedTextModel text) {
		return this.localizedTexts.remove(text);
	}
	
	/**
	 * Tests whether a this TextedTag contains a LocalizedTextModel with a certain LanguageCode.
	 * @param langCode the LanguageCode to test for
	 * @return true if the LanguageCode is contained, false if otherwise
	 */
	public boolean containsLocalizedText(LanguageCode languageCode) {
		for (LocalizedTextModel _localizedText : localizedTexts) {
			if (_localizedText.getLanguageCode() == languageCode) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Retrieve the text in a certain language.
	 * @param langCode the LanguageCode to look for
	 * @return the LocalizedTextModel with the LanguageCode found or null if no such LocalizedText exists.
	 */
	public LocalizedTextModel getLocalizedText(LanguageCode languageCode) {
		for (LocalizedTextModel _localizedText : localizedTexts) {
			if (_localizedText.getLanguageCode() == languageCode) {
				return _localizedText;
			}
		}
		return null;		
	}
}
