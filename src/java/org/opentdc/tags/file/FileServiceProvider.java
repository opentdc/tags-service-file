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
import org.opentdc.tags.TagsModel;
import org.opentdc.tags.ServiceProvider;
import org.opentdc.service.LocalizedTextModel;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

/**
 * A file-based or transient implementation of the Tags service.
 * @author Bruno Kaiser
 *
 */
public class FileServiceProvider extends AbstractFileServiceProvider<TextedTag> implements ServiceProvider {
	private static Map<String, TextedTag> index = null;
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
			index = new ConcurrentHashMap<String, TextedTag>();
			textIndex = new ConcurrentHashMap<String, LocalizedTextModel>();
			List<TextedTag> _tags = importJson();
			for (TextedTag _tag : _tags) {
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
	public ArrayList<TagsModel> list(
		String queryType,
		String query,
		int position,
		int size
	) {
		ArrayList<TagsModel> _tags = new ArrayList<TagsModel>();
		for (TextedTag _tag : index.values()) {
			_tags.add(_tag.getModel());
		}
		Collections.sort(_tags, TagsModel.TagComparator);
		ArrayList<TagsModel> _selection = new ArrayList<TagsModel>();
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
	 * @see org.opentdc.tags.ServiceProvider#create(org.opentdc.tags.TagsModel)
	 */
	@Override
	public TagsModel create(
		TagsModel tag) 
	throws DuplicateException, ValidationException {
		TextedTag _textedTag = null;
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(tag) + ")");
		String _id = tag.getId();
		Date _date = new Date();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
			tag.setId(_id);
			tag.setCounter(1);
			tag.setCreatedAt(_date);
			tag.setCreatedBy(getPrincipal());
			tag.setModifiedAt(_date);
			tag.setModifiedBy(getPrincipal());
			_textedTag = new TextedTag();
			_textedTag.setModel(tag);
		} else {
			_textedTag = index.get(_id);
			if (_textedTag != null) {
				TagsModel _tm = _textedTag.getModel();
				_tm.setCounter(_tm.getCounter() + 1);
				_tm.setModifiedAt(_date);
				_tm.setModifiedBy(getPrincipal());
				_textedTag.setModel(_tm);
				index.put(_id, _textedTag);
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("tag <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		index.put(_id,  _textedTag);
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(_textedTag.getModel()) + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
		return _textedTag.getModel();
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#read(java.lang.String)
	 */
	@Override
	public TagsModel read(
		String id) 
	throws NotFoundException {
		TagsModel _tag = readTextedTag(id).getModel();
		logger.info("read(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tag));
		return _tag;
	}
	
	/**
	 * Retrieve a TextedTag from the index.
	 * @param id
	 * @return the TextedTag
	 * @throws NotFoundException if the index did not contain a TextedTag with this id
	 */
	private TextedTag readTextedTag(
			String id
	) throws NotFoundException {
		TextedTag _textedTag = index.get(id);
		if (_textedTag == null) {
			throw new NotFoundException("tag <" + id
					+ "> was not found.");
		}
		logger.info("readTextedTag(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_textedTag));
		return _textedTag;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#update(java.lang.String, org.opentdc.tags.TagsModel)
	 */
	@Override
	public TagsModel update(
		String id, 
		TagsModel tag
	) throws NotFoundException, ValidationException {
		TextedTag _textedTag = readTextedTag(id);
		TagsModel _tagsModel = _textedTag.getModel();
		if (! _tagsModel.getCreatedAt().equals(tag.getCreatedAt())) {
			logger.warning("tag <" + id + ">: ignoring createdAt value <" + tag.getCreatedAt().toString() + 
					"> because it was set on the client.");
		}
		if (! _tagsModel.getCreatedBy().equalsIgnoreCase(tag.getCreatedBy())) {
			logger.warning("tag <" + id + ">: ignoring createdBy value <" + tag.getCreatedBy() +
					"> because it was set on the client.");
		}
		if (_tagsModel.getCounter() != tag.getCounter()) {
			logger.warning("tag <" + id + ">: ignoring counter value <" + tag.getCounter() +
					">because the counter can not be changed by the client.");
		}
		_tagsModel.setModifiedAt(new Date());
		_tagsModel.setModifiedBy(getPrincipal());
		_textedTag.setModel(_tagsModel);
		index.put(id, _textedTag);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tagsModel));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _tagsModel;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#delete(java.lang.String)
	 */
	@Override
	public void delete(
		String id) 
	throws NotFoundException, InternalServerErrorException {
		TextedTag _textedTag = readTextedTag(id);
		TagsModel _tagsModel = _textedTag.getModel();
		if (_tagsModel.getCounter() == 1) {	// remove the tag object from the index
			if (index.remove(id) == null) {
				throw new InternalServerErrorException("tag <" + id
					+ "> can not be removed, because it does not exist in the index");
			} else {			// remove was ok
				logger.info("delete(" + id + ") -> removed from index.");
			}
		} else if (_tagsModel.getCounter() < 1) {
				throw new InternalServerErrorException("tag <" + id + 
						">: counter has invalid value <" + _tagsModel.getCounter() + "> (should be >= 1).");
		}
		else { // counter > 1 -> decrement it
			_tagsModel.setCounter(_tagsModel.getCounter() - 1);
			_textedTag.setModel(_tagsModel);
			index.put(id, _textedTag);
			logger.info("delete(" + id + ") -> decrement counter to <" + _tagsModel.getCounter() + ">.");
		}
		if (isPersistent) {
			exportJson(index.values());
		}
	}

	/************************************** localized texts (lang) ************************************/
	@Override
	public List<LocalizedTextModel> listTexts(
			String tid, 
			String queryType,
			String query, 
			int position, 
			int size) {
		List<LocalizedTextModel> _localizedTexts = readTextedTag(tid).getLocalizedTexts();
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
		TextedTag _textedTag = readTextedTag(tid);
		if (tag.getLangCode() == null) {
			throw new ValidationException("LocalizedText <" + tid + "/lang/" + tag.getId() + 
					"> must contain a LanguageCode.");
		}
		if (_textedTag.containsLocalizedText(tag.getLangCode())) {
			throw new DuplicateException("LocalizedText with LanguageCode <" + tag.getLangCode() + 
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
		_textedTag.addText(tag);
		logger.info("createText(" + tid + "/lang/" + tag.getId() + ") -> " + PrettyPrinter.prettyPrintAsJSON(tag));
		if (isPersistent) {
			exportJson(index.values());
		}
		return tag;
	}

	@Override
	public LocalizedTextModel readText(
			String tid, 
			String lid)
			throws NotFoundException {
		readTextedTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		logger.info("readText(" + tid + "/lang/" + lid + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_localizedText));
		return _localizedText;
	}

	@Override
	public LocalizedTextModel updateText(
			String tid, 
			String lid,
			LocalizedTextModel tag) 
					throws NotFoundException, ValidationException {
		readTextedTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		if (! _localizedText.getCreatedAt().equals(tag.getCreatedAt())) {
			logger.warning("LocalizedText <" + tid + "/lang/" + lid + ">: ignoring createAt value <" 
					+ tag.getCreatedAt().toString() + "> because it was set on the client.");
		}
		if (! _localizedText.getCreatedBy().equalsIgnoreCase(tag.getCreatedBy())) {
			logger.warning("LocalizedText <" + tid + "/lang/" + lid + ">: ignoring createBy value <"
					+ tag.getCreatedBy() + "> because it was set on the client.");
		}
		if (_localizedText.getLangCode() != tag.getLangCode()) {
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

	@Override
	public void deleteText(
			String tid, 
			String lid) 
					throws NotFoundException, InternalServerErrorException {
		TextedTag _textedTag = readTextedTag(tid);
		LocalizedTextModel _localizedText = textIndex.get(lid);
		if (_localizedText == null) {
			throw new NotFoundException("LocalizedText <" + tid + "/lang/" + lid +
					"> was not found.");
		}
		
		// 1) remove the LocalizedText from its TextedTag
		if (_textedTag.removeText(_localizedText) == false) {
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
}
