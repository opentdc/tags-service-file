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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.tags.TagsModel;
import org.opentdc.tags.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * A file-based or transient implementation of the Tags service.
 * @author Bruno Kaiser
 *
 */
public class FileServiceProvider implements AbstractFileServiceProvider<TagsModel> implements ServiceProvider {
	
	private static Map<String, TagsModel> index = null;
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
		if (index == null) {
			index = new HashMap<String, TagsModel>();
			List<TagsModel> _tags = importJson();
			for (TagsModel _tag : _tags) {
				index.put(_tag.getId(), _tag);
			}
			logger.info(_tags.size() + " Tags imported.");
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#list(java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public ArrayList<TagsModel> list(
		String queryType,
		String query,
		long position,
		long size
	) {
		ArrayList<TagsModel> _tags = new ArrayList<TagsModel>(index.values());
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
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(tag) + ")");
		String _id = tag.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("tag <" + _id + "> exists already.");
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("tag <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		// enforce mandatory fields
		if (tag.getTitle() == null || tag.getTitle().length() == 0) {
			throw new ValidationException("tag <" + _id + 
					"> must contain a valid title.");
		}

		tag.setId(_id);
		Date _date = new Date();
		tag.setCreatedAt(_date);
		tag.setCreatedBy(getPrincipal());
		tag.setModifiedAt(_date);
		tag.setModifiedBy(getPrincipal());
		index.put(_id, tag);
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(tag) + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
		return tag;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#read(java.lang.String)
	 */
	@Override
	public TagsModel read(
		String id) 
	throws NotFoundException {
		TagsModel _tag = index.get(id);
		if (_tag == null) {
			throw new NotFoundException("no tag with ID <" + id
					+ "> was found.");
		}
		logger.info("read(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tag));
		return _tag;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#update(java.lang.String, org.opentdc.tags.TagsModel)
	 */
	@Override
	public TagsModel update(
		String id, 
		TagsModel tag
	) throws NotFoundException, ValidationException {
		TagsModel _tag = index.get(id);
		if(_tag == null) {
			throw new NotFoundException("no tag with ID <" + id
					+ "> was found.");
		} 
		if (! _tag.getCreatedAt().equals(tag.getCreatedAt())) {
			logger.warning("tag <" + id + ">: ignoring createdAt value <" + tag.getCreatedAt().toString() + 
					"> because it was set on the client.");
		}
		if (! _tag.getCreatedBy().equalsIgnoreCase(tag.getCreatedBy())) {
			logger.warning("tag <" + id + ">: ignoring createdBy value <" + tag.getCreatedBy() +
					"> because it was set on the client.");
		}
		_tag.setTitle(tag.getTitle());
		_tag.setDescription(tag.getDescription());
		_tag.setModifiedAt(new Date());
		_tag.setModifiedBy(getPrincipal());
		index.put(id, _tag);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_tag));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _tag;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.tags.ServiceProvider#delete(java.lang.String)
	 */
	@Override
	public void delete(
		String id) 
	throws NotFoundException. InternalServerErrorException {
		TagsModel _tag = index.get(id);
		if (_tag == null) {
			throw new NotFoundException("tag <" + id
					+ "> was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("tag <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("delete(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
