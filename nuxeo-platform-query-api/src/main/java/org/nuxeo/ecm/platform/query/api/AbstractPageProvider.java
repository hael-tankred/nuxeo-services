/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.query.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.runtime.api.Framework;

/**
 * Basic implementation for a {@link PageProvider}
 *
 * @author Anahide Tchertchian
 */
public abstract class AbstractPageProvider<T> implements PageProvider<T> {

    private static final Log log = LogFactory.getLog(AbstractPageProvider.class);

    private static final long serialVersionUID = 1L;

    protected String name;

    protected long offset = 0;

    protected long pageSize = 0;

    protected long maxPageSize = getDefaultMaxPageSize();

    protected long resultsCount = UNKNOWN_SIZE;

    protected int currentEntryIndex = 0;

    protected List<SortInfo> sortInfos;

    protected boolean sortable = false;

    protected List<T> selectedEntries;

    protected PageSelections<T> currentSelectPage;

    protected Map<String, Serializable> properties;

    protected Object[] parameters;

    protected DocumentModel searchDocumentModel;

    protected String errorMessage;

    protected Throwable error;

    protected PageProviderDefinition definition;

    public abstract List<T> getCurrentPage();

    /**
     * Page change hook, to override for custom behavior
     */
    protected void pageChanged() {
        currentEntryIndex = 0;
        currentSelectPage = null;
    }

    public void firstPage() {
        if (pageSize == 0) {
            // do nothing
            return;
        }
        if (offset != 0) {
            offset = 0;
            pageChanged();
        }
    }

    /**
     * @deprecated: use {@link #firstPage()} instead
     */
    public void rewind() {
        firstPage();
    }

    public long getCurrentPageIndex() {
        if (pageSize == 0) {
            return 0;
        }
        return offset / pageSize;
    }

    public long getCurrentPageOffset() {
        return offset;
    }

    public long getCurrentPageSize() {
        List<T> currentItems = getCurrentPage();
        if (currentItems != null) {
            return currentItems.size();
        }
        return 0;
    }

    public String getName() {
        return name;
    }

    public long getNumberOfPages() {
        if (pageSize == 0) {
            return 1;
        }
        return (int) (1 + (getResultsCount() - 1) / pageSize);
    }

    public List<T> setCurrentPage(long page) {
        long oldOffset = offset;
        offset = page * pageSize;
        pageChanged();
        List<T> res = getCurrentPage();
        // sanity check in case given page is not present in result provider
        if (page >= getNumberOfPages()) {
            // go back to old offset
            log.warn(String.format(
                    "Provider '%s' does not have a page with number '%s': "
                            + "go back to old page", getName(),
                    Long.valueOf(page)));
            offset = oldOffset;
            pageChanged();
            res = getCurrentPage();
        }
        return res;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        if (this.pageSize != pageSize) {
            this.pageSize = pageSize;
            // reset offset too
            offset = 0;
            refresh();
        }
    }

    public List<SortInfo> getSortInfos() {
        // break reference
        List<SortInfo> res = new ArrayList<SortInfo>();
        if (sortInfos != null) {
            res.addAll(sortInfos);
        }
        return res;
    }

    public SortInfo getSortInfo() {
        if (sortInfos != null && !sortInfos.isEmpty()) {
            return sortInfos.get(0);
        }
        return null;
    }

    protected boolean sortInfoChanged(List<SortInfo> oldSortInfos,
            List<SortInfo> newSortInfos) {
        if (oldSortInfos == null && newSortInfos == null) {
            return false;
        } else if (oldSortInfos == null) {
            oldSortInfos = Collections.emptyList();
        } else if (newSortInfos == null) {
            newSortInfos = Collections.emptyList();
        }
        if (oldSortInfos.size() != newSortInfos.size()) {
            return true;
        }
        for (int i = 0; i < oldSortInfos.size(); i++) {
            SortInfo oldSort = oldSortInfos.get(i);
            SortInfo newSort = newSortInfos.get(i);
            if (oldSort == null && newSort == null) {
                continue;
            } else if (oldSort != null || newSort != null) {
                return true;
            }
            if (!oldSort.equals(newSort)) {
                return true;
            }
        }
        return false;
    }

    public void setSortInfos(List<SortInfo> sortInfo) {
        if (sortInfoChanged(this.sortInfos, sortInfo)) {
            this.sortInfos = sortInfo;
            refresh();
        }
    }

    public void setSortInfo(SortInfo sortInfo) {
        List<SortInfo> newSortInfos = new ArrayList<SortInfo>();
        if (sortInfo != null) {
            newSortInfos.add(sortInfo);
        }
        setSortInfos(newSortInfos);
    }

    public void setSortInfo(String sortColumn, boolean sortAscending,
            boolean removeOtherSortInfos) {
        if (removeOtherSortInfos) {
            SortInfo sortInfo = new SortInfo(sortColumn, sortAscending);
            setSortInfo(sortInfo);
        } else {
            if (getSortInfoIndex(sortColumn, sortAscending) != -1) {
                // do nothing: sort on this column is not set
            } else if (getSortInfoIndex(sortColumn, !sortAscending) != -1) {
                // change direction
                List<SortInfo> newSortInfos = new ArrayList<SortInfo>();
                for (SortInfo sortInfo : getSortInfos()) {
                    if (sortColumn.equals(sortInfo.getSortColumn())) {
                        newSortInfos.add(new SortInfo(sortColumn, sortAscending));
                    } else {
                        newSortInfos.add(sortInfo);
                    }
                }
                setSortInfos(newSortInfos);
            } else {
                // just add it
                addSortInfo(sortColumn, sortAscending);
            }
        }
    }

    public void addSortInfo(String sortColumn, boolean sortAscending) {
        SortInfo sortInfo = new SortInfo(sortColumn, sortAscending);
        List<SortInfo> sortInfos = getSortInfos();
        if (sortInfos == null) {
            setSortInfo(sortInfo);
        } else {
            sortInfos.add(sortInfo);
            setSortInfos(sortInfos);
        }
    }

    public int getSortInfoIndex(String sortColumn, boolean sortAscending) {
        List<SortInfo> sortInfos = getSortInfos();
        if (sortInfos == null || sortInfos.isEmpty()) {
            return -1;
        } else {
            SortInfo sortInfo = new SortInfo(sortColumn, sortAscending);
            return sortInfos.indexOf(sortInfo);
        }
    }

    public boolean isNextPageAvailable() {
        if (pageSize == 0) {
            return false;
        }
        return getResultsCount() > pageSize + offset;
    }

    public boolean isPreviousPageAvailable() {
        return offset > 0;
    }

    public void lastPage() {
        if (pageSize == 0) {
            // do nothing
            return;
        }
        if (getResultsCount() % pageSize == 0) {
            offset = getResultsCount() - pageSize;
        } else {
            offset = (int) (getResultsCount() - getResultsCount() % pageSize);
        }
        pageChanged();
    }

    /**
     * @deprecated: use {@link #lastPage()} instead
     */
    public void last() {
        lastPage();
    }

    public void nextPage() {
        if (pageSize == 0) {
            // do nothing
            return;
        }
        offset += pageSize;
        pageChanged();
    }

    /**
     * @deprecated: use {@link #nextPage()} instead
     */
    public void next() {
        nextPage();
    }

    public void previousPage() {
        if (pageSize == 0) {
            // do nothing
            return;
        }
        if (offset >= pageSize) {
            offset -= pageSize;
            pageChanged();
        }
    }

    /**
     * @deprecated: use {@link #previousPage()} instead
     */
    public void previous() {
        previousPage();
    }

    public void refresh() {
        resultsCount = UNKNOWN_SIZE;
        currentSelectPage = null;
        errorMessage = null;
        error = null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrentPageStatus() {
        long total = getNumberOfPages();
        long current = getCurrentPageIndex() + 1;
        if (total == UNKNOWN_SIZE) {
            return String.format("%d", Long.valueOf(current));
        } else {
            return String.format("%d/%d", Long.valueOf(current),
                    Long.valueOf(total));
        }
    }

    public boolean isNextEntryAvailable() {
        if (pageSize == 0) {
            return currentEntryIndex < getResultsCount() - 1;
        } else {
            return (currentEntryIndex < (getResultsCount() % pageSize) - 1 || isNextPageAvailable());
        }
    }

    public boolean isPreviousEntryAvailable() {
        return (currentEntryIndex != 0 || isPreviousPageAvailable());
    }

    public void nextEntry() {
        if ((pageSize == 0 && currentEntryIndex < getResultsCount())
                || (currentEntryIndex < getCurrentPageSize() - 1)) {
            currentEntryIndex++;
            return;
        }
        if (!isNextPageAvailable()) {
            return;
        }

        nextPage();
        List<T> currentPage = getCurrentPage();
        if (currentPage == null || currentPage.isEmpty()) {
            // things have changed since last query
            currentEntryIndex = 0;
        } else {
            currentEntryIndex = 0;
        }
    }

    public void previousEntry() {
        if (currentEntryIndex > 0) {
            currentEntryIndex--;
            return;
        }
        if (!isPreviousPageAvailable()) {
            return;
        }

        previousPage();
        List<T> currentPage = getCurrentPage();
        if (currentPage == null || currentPage.isEmpty()) {
            // things may have changed
            currentEntryIndex = 0;
        } else {
            currentEntryIndex = (new Long(getPageSize() - 1)).intValue();
        }
    }

    public T getCurrentEntry() {
        List<T> currentPage = getCurrentPage();
        if (currentPage == null || currentPage.isEmpty()) {
            return null;
        }
        return currentPage.get(currentEntryIndex);
    }

    public void setCurrentEntry(T entry) throws ClientException {
        List<T> currentPage = getCurrentPage();
        if (currentPage == null || currentPage.isEmpty()) {
            throw new ClientException(String.format(
                    "Entry '%s' not found in current page", entry));
        }
        int i = currentPage.indexOf(entry);
        if (i == -1) {
            throw new ClientException(String.format(
                    "Entry '%s' not found in current page", entry));
        }
        currentEntryIndex = i;
    }

    public void setCurrentEntryIndex(long index) throws ClientException {
        int intIndex = new Long(index).intValue();
        List<T> currentPage = getCurrentPage();
        if (currentPage == null || currentPage.isEmpty()) {
            throw new ClientException(
                    String.format("Index %s not found in current page",
                            new Integer(intIndex)));
        }
        if (index >= currentPage.size()) {
            throw new ClientException(
                    String.format("Index %s not found in current page",
                            new Integer(intIndex)));
        }
        currentEntryIndex = intIndex;
    }

    public long getResultsCount() {
        return resultsCount;
    }

    public Map<String, Serializable> getProperties() {
        // break reference
        return new HashMap<String, Serializable>(properties);
    }

    public void setProperties(Map<String, Serializable> properties) {
        this.properties = properties;
    }

    public void setResultsCount(long resultsCount) {
        this.resultsCount = resultsCount;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public boolean isSortable() {
        return sortable;
    }

    public PageSelections<T> getCurrentSelectPage() {
        if (currentSelectPage == null) {
            List<PageSelection<T>> entries = new ArrayList<PageSelection<T>>();
            List<T> currentPage = getCurrentPage();
            currentSelectPage = new PageSelections<T>();
            currentSelectPage.setName(name);
            if (currentPage != null && !currentPage.isEmpty()) {
                if (selectedEntries == null || selectedEntries.isEmpty()) {
                    // no selection at all
                    for (int i = 0; i < currentPage.size(); i++) {
                        entries.add(new PageSelection<T>(currentPage.get(i),
                                false));
                    }
                } else {
                    boolean allSelected = true;
                    for (int i = 0; i < currentPage.size(); i++) {
                        T entry = currentPage.get(i);
                        Boolean selected = Boolean.valueOf(selectedEntries.contains(entry));
                        if (!Boolean.TRUE.equals(selected)) {
                            allSelected = false;
                        }
                        entries.add(new PageSelection<T>(entry,
                                selected.booleanValue()));
                    }
                    if (allSelected) {
                        currentSelectPage.setSelected(true);
                    }
                }
            }
            currentSelectPage.setEntries(entries);
        }
        return currentSelectPage;
    }

    public void setSelectedEntries(List<T> entries) {
        this.selectedEntries = entries;
        // reset current select page so that it's rebuilt
        currentSelectPage = null;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    public DocumentModel getSearchDocumentModel() {
        return searchDocumentModel;
    }

    protected boolean searchDocumentModelChanged(DocumentModel oldDoc,
            DocumentModel newDoc) {
        if (oldDoc == null && newDoc == null) {
            return false;
        } else if (oldDoc == null || newDoc == null) {
            return true;
        }
        // do not compare properties and assume it's changed
        return true;
    }

    public void setSearchDocumentModel(DocumentModel searchDocumentModel) {
        if (searchDocumentModelChanged(this.searchDocumentModel,
                searchDocumentModel)) {
            refresh();
        }
        this.searchDocumentModel = searchDocumentModel;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getError() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    @Override
    public PageProviderDefinition getDefinition() {
        return definition;
    }

    @Override
    public void setDefinition(PageProviderDefinition providerDefinition) {
        this.definition = providerDefinition;
    }

    public long getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(long maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * Returns the minimal value for the max page size, taking the lower value
     * between the requested page size and the maximum accepted page size.
     *
     * @since 5.4.2
     */
    public long getMinMaxPageSize() {
        long pageSize = getPageSize();
        long maxPageSize = getMaxPageSize();
        if (maxPageSize < 0) {
            maxPageSize = getDefaultMaxPageSize();
        }
        if (pageSize <= 0) {
            return maxPageSize;
        }
        if (maxPageSize > 0 && maxPageSize < pageSize) {
            return maxPageSize;
        }
        return pageSize;
    }

    protected long getDefaultMaxPageSize() {
        String customDefaultMaxPageSize = Framework.getProperty("org.nuxeo.ecm.platform.query.api.PageProvider.customDefaultMaxPageSize");
        if (customDefaultMaxPageSize == null) {
            return DEFAULT_MAX_PAGE_SIZE;
        }
        try {
            return Long.parseLong(customDefaultMaxPageSize);
        } catch (NumberFormatException e) {
            log.warn("No valid custom max page size defined (\"org.nuxeo.ecm.platform.query.api.PageProvider.customDefaultMaxPageSize\"): Should be a long value.");
        }
        return DEFAULT_MAX_PAGE_SIZE;
    }

}
