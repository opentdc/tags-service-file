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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.tags.SingleLangTag;
import org.opentdc.tags.TagModel;
import org.opentdc.tags.ServiceProvider;
import org.opentdc.service.LocalizedTextModel;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.LanguageCode;
import org.opentdc.util.PrettyPrinter;

/**
 * A file-based or transient implementation of the Tags service.
 * @author Bruno Kaiser
 *
 */
public class FileServiceProvider extends AbstractFileServiceProvider<MultiLangTag> implements ServiceProvider {
	private static Map<String, MultiLangTag> index = null;
	private static Map<String, LocalizedTextModel> textIndex = null;
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	/**
	 * Constructor.
	 * @param context the servlet context.
	 * @param prefix the simple class name of the service provider
	 * @throws IOException
	 */
	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		logger.info("tags-service.FileServiceProvider.Constructor()");
		if (index == null) {
			index = new ConcurrentHashMap<String, MultiLangTag>();
			textIndex = new ConcurrentHashMap<String, LocalizedTextModel>();
			List<MultiLangTag> _tags = importJson();
			for (MultiLangTag _tag : _tags) {
				index.put(_tag.getModel().getId(), _tag);
				for (LocalizedTextModel _tm : _tag.getLocalizedTexts()) {
					textIndex.put(_tm.getId(), _tm);
				}
			}
			logger.info("indexed " +
					index.size() + " tags and " +
					textIndex.size() + " localized texts.");
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#list(java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public ArrayList<SingleLangTag> list(
		String query,
		String queryType,
		int position,
		int size) {
		ArrayList<SingleLangTag> _tags = new ArrayList<SingleLangTag>();
		LocalizedTextModel _ltm = null;
		LanguageCode _lc = LanguageCode.getLanguageCodeFromQuery(query);
		for (MultiLangTag _multiLangTag : index.values()) {
			if (_lc != null) { // return only the texts in this specific language
				_ltm = _multiLangTag.getLocalizedText(_lc);
				if (_ltm != null) {
					_tags.add(new SingleLangTag(_multiLangTag.getModel().getId(), _ltm, getPrincipal()));
				}
			} else {  // return all texts in all languages
				List<LocalizedTextModel> _texts = _multiLangTag.getLocalizedTexts();
				for (LocalizedTextModel _lm : _texts) {
					_tags.add(new SingleLangTag(_multiLangTag.getModel().getId(), _lm, getPrincipal()));
				}
			}
		}
		Collections.sort(_tags, SingleLangTag.TagComparator);
		ArrayList<SingleLangTag> _selection = new ArrayList<SingleLangTag>();
		for (int i = 0; i < _tags.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_tags.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " tags.");
		return _selection;
	}
			
	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#create(org.opentdc.tags.TagModel)
	 */
	@Override
	public TagModel create(
		TagModel tag) 
	throws DuplicateException, ValidationException {
		MultiLangTag _multiLangTag = null;
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(tag) + ")");
		String _id = tag.getId();
		Date _date = new Date();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
			tag.setId(_id);
			tag.setCreatedAt(_date);
			tag.setCreatedBy(getPrincipal());
			tag.setModifiedAt(_date);
			tag.setModifiedBy(getPrincipal());
			_multiLangTag = new MultiLangTag();
			_multiLangTag.setModel(tag);
		} else {
			_multiLangTag = index.get(_id);
			if (_multiLangTag != null) {
				throw new DuplicateException("tag <" + _id + "> exists already.");
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("tag <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		index.put(_id,  _multiLangTag);
		logger.info("create() -> " + PrettyPrinter.prettyPrintAsJSON(_multiLangTag.getModel()));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _multiLangTag.getModel();
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#read(java.lang.String)
	 */
	@Override
	public TagModel read(
		String id) 
	throws NotFoundException {
		TagModel _tag = readMultiLangTag(id).getModel();
		logger.info("read(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tag));
		return _tag;
	}
		
	/**
	 * Retrieve a MultiLangTag from the index.
	 * @param id
	 * @return the MultiLangTag
	 * @throws NotFoundException if the index did not contain a MultiLangTag with this id
	 */
	public static MultiLangTag readMultiLangTag(
			String id
	) throws NotFoundException {
		MultiLangTag _multiLangTag = index.get(id);
		if (_multiLangTag == null) {
			throw new NotFoundException("tag <" + id
					+ "> was not found.");
		}
		logger.info("readMultiLangTag(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_multiLangTag));
		return _multiLangTag;
	}
	
	/**
	 * @param id
	 * @param lang
	 * @return
	 * @throws NotFoundException
	 */
	public static String getLocalizedText(
			String id,
			LanguageCode lang) 
			throws NotFoundException {
		if (lang == null) {
			logger.warning("lang is null; using default");
			lang = LanguageCode.getDefaultLanguageCode();
		}
		LocalizedTextModel _ltm = readMultiLangTag(id).getLocalizedText(lang);
		if (_ltm != null) {
			return _ltm.getText();
		}
		else {
			return "UNDEFINED";
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#update(java.lang.String, org.opentdc.tags.TagModel)
	 */
	@Override
	public TagModel update(
		String id, 
		TagModel tag
	) throws NotFoundException, ValidationException {
		MultiLangTag _multiLangTag = readMultiLangTag(id);
		TagModel _tagModel = _multiLangTag.getModel();
		if (! _tagModel.getCreatedAt().equals(tag.getCreatedAt())) {
			logger.warning("tag <" + id + ">: ignoring createdAt value <" + tag.getCreatedAt().toString() + 
					"> because it was set on the client.");
		}
		if (! _tagModel.getCreatedBy().equalsIgnoreCase(tag.getCreatedBy())) {
			logger.warning("tag <" + id + ">: ignoring createdBy value <" + tag.getCreatedBy() +
					"> because it was set on the client.");
		}
		_tagModel.setModifiedAt(new Date());
		_tagModel.setModifiedBy(getPrincipal());
		_multiLangTag.setModel(_tagModel);
		index.put(id, _multiLangTag);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tagModel));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _tagModel;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#delete(java.lang.String)
	 */
	@Override
	public void delete(
		String id) 
	throws NotFoundException, InternalServerErrorException {
		MultiLangTag _multiLangTag = readMultiLangTag(id);  // throws NotFound
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("tag <" + id
				+ "> can not be removed, because it does not exist in the index");
		} else {			// remove was ok
			// remove all LocalizedTexts members
			for (LocalizedTextModel _ltm : _multiLangTag.getLocalizedTexts()) {
				if (textIndex.remove(_ltm.getId()) == null) {
					throw new InternalServerErrorException("tag <" + id +
						">: LocalizedText <" + _ltm.getId() + 
						"> could not be removed, because it does not exist in the index.");
				} else {
					logger.info("delete(" + id + "): LocalizedText <" + _ltm.getId() + "> removed from the index.");
				}
			}
			logger.info("delete(" + id + ") -> tag removed from index.");
		}
		if (isPersistent) {
			exportJson(index.values());
		}
	}

	/************************************** localized texts (lang) ************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#listTexts(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<LocalizedTextModel> listTexts(
			String tid, 
			String queryType,
			String query, 
			int position, 
			int size) {
		List<LocalizedTextModel> _localizedTexts = readMultiLangTag(tid).getLocalizedTexts();
		Collections.sort(_localizedTexts, LocalizedTextModel.LocalizedTextComparator);
		
		ArrayList<LocalizedTextModel> _selection = new ArrayList<LocalizedTextModel>();
		for (int i = 0; i < _localizedTexts.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_localizedTexts.get(i));
			}
		}
		logger.info("listTexts(<" + tid + ">, <" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size()
				+ " values");
		return _selection;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#createText(java.lang.String, org.opentdc.service.LocalizedTextModel)
	 */
	@Override
	public LocalizedTextModel createText(
			String tid, 
			LocalizedTextModel tag)
			throws DuplicateException, ValidationException {
		if (tag.getText() == null || tag.getText().isEmpty()) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + tag.getId() + 
					"> must contain a valid text.");
		}
		// enforce that the title is a single word
		StringTokenizer _tokenizer = new StringTokenizer(tag.getText());
		if (_tokenizer.countTokens() != 1) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + tag.getId() + 
					"> must consist of exactly one word <" + tag.getText() + "> (is " + _tokenizer.countTokens() + ").");
		}
		MultiLangTag _multiLangTag = readMultiLangTag(tid);
		if (tag.getLanguageCode() == null) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + tag.getId() + 
					"> must contain a LanguageCode.");
		}
		if (_multiLangTag.containsLocalizedText(tag.getLanguageCode())) {
			throw new DuplicateException("LocalizedText with LanguageCode <" + tag.getLanguageCode() + 
					"> exists already in tag <" + tid + ">.");
		}
		String _id = tag.getId();
		if (_id == null || _id.isEmpty()) {
			_id = UUID.randomUUID().toString();
		} else {
			if (textIndex.get(_id) != null) {
				throw new DuplicateException("LocalizedText with id <" + _id + 
						"> exists alreday in index.");
			}
			else {
				throw new ValidationException("LocalizedText <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}

		tag.setId(_id);
		Date _date = new Date();
		tag.setCreatedAt(_date);
		tag.setCreatedBy(getPrincipal());
		tag.setModifiedAt(_date);
		tag.setModifiedBy(getPrincipal());
		
		textIndex.put(_id, tag);
		_multiLangTag.addText(tag);
		logger.info("createText(" + tid + "/lang/" + tag.getId() + ") -> " + PrettyPrinter.prettyPrintAsJSON(tag));
		if (isPersistent) {
			exportJson(index.values());
		}
		return tag;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#readText(java.lang.String, java.lang.String)
	 */
	@Override
	public LocalizedTextModel readText(
			String tid, 
			String lid)
			throws NotFoundException {
		readMultiLangTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		logger.info("readText(" + tid + "/lang/" + lid + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_localizedText));
		return _localizedText;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#updateText(java.lang.String, java.lang.String, org.opentdc.service.LocalizedTextModel)
	 */
	@Override
	public LocalizedTextModel updateText(
			String tid, 
			String lid,
			LocalizedTextModel tag) 
					throws NotFoundException, ValidationException {
		readMultiLangTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		if (tag.getText() == null || tag.getText().isEmpty()) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + lid + 
					"> must contain a valid text.");
		}
		// enforce that the title is a single word
		StringTokenizer _tokenizer = new StringTokenizer(tag.getText());
		if (_tokenizer.countTokens() != 1) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + lid + 
					"> must consist of exactly one word <" + tag.getText() + "> (is " + _tokenizer.countTokens() + ").");
		}
		if (! _localizedText.getCreatedAt().equals(tag.getCreatedAt())) {
			logger.warning("LocalizedText <" + tid + "/lang/" + lid + ">: ignoring createAt value <" 
					+ tag.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _localizedText.getCreatedBy().equalsIgnoreCase(tag.getCreatedBy())) {
			logger.warning("LocalizedText <" + tid + "/lang/" + lid + ">: ignoring createBy value <"
					+ tag.getCreatedBy() + "> because it was set on the client.");
		}
		if (_localizedText.getLanguageCode() != tag.getLanguageCode()) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + lid + 
					">: it is not allowed to change the LanguageCode.");
		}
		_localizedText.setText(tag.getText());
		_localizedText.setModifiedAt(new Date());
		_localizedText.setModifiedBy(getPrincipal());
		textIndex.put(lid, _localizedText);
		logger.info("updateText(" + tid + ", " + lid + ") -> " + PrettyPrinter.prettyPrintAsJSON(_localizedText));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _localizedText;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#deleteText(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteText(
			String tid, 
			String lid) 
					throws NotFoundException, InternalServerErrorException {
		MultiLangTag _multiLangTag = readMultiLangTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		
		// 1) remove the LocalizedText from its MultiLangTag
		if (_multiLangTag.removeText(_localizedText) == false) {
			throw new InternalServerErrorException("LocalizedText <" + tid + "/lang/" + lid
					+ "> can not be removed, because it is an orphan.");
		}
		// 2) remove the LocalizedText from the index
		if (textIndex.remove(lid) == null) {
			throw new InternalServerErrorException("LocalizedText <" + tid + "/lang/" + lid
					+ "> can not be removed, because it does not exist in the index.");
		}
		
			
		logger.info("deleteText(" + tid + ", " + lid + ") -> OK");
		if (isPersistent) {
			exportJson(index.values());
		}		
	}

	public static TagModel getTagsModel(String tagId) {
		// TODO Auto-generated method stub
		return null;
	}
}
