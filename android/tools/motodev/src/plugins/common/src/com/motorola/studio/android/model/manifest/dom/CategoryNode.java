/*
* Copyright (C) 2012 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.motorola.studio.android.model.manifest.dom;

import java.util.List;

import org.eclipse.core.runtime.IStatus;

/**
 * Class that represents a <category> node on AndroidManifest.xml file
 */
public class CategoryNode extends AbstractSimpleNameNode
{
    /**
     * Default constructor
     * 
     * @param name The name property
     */
    public CategoryNode(String name)
    {
        super(name);
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.model.manifest.dom.AndroidManifestNode#getNodeType()
     */
    @Override
    public NodeType getNodeType()
    {
        return NodeType.Category;
    }

    /* (non-Javadoc)
     * @see com.motorola.studio.android.model.manifest.dom.AndroidManifestNode#getSpecificNodeErrors()
     */
    @Override
    protected List<IStatus> getSpecificNodeProblems()
    {
        return null;
    }
}
