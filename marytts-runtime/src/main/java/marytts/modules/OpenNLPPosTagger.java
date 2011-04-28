/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.server.MaryProperties;
import marytts.util.MaryUtils;
import marytts.util.dom.MaryDomUtils;
import opennlp.maxent.MaxentModel;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.tools.postag.DefaultPOSContextGenerator;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.TagDictionary;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.traversal.NodeIterator;
import org.w3c.dom.traversal.TreeWalker;


/**
 * Part-of-speech tagger using OpenNLP.
 *
 * @author Marc Schr&ouml;der
 */

public class OpenNLPPosTagger extends InternalModule
{
    private String propertyPrefix;
    private POSTaggerME tagger;
    private Map<String,String> posMapper = null;

    /**
     * Constructor which can be directly called from init info in the config file.
     * Different languages can call this code with different settings.
     * @param locale a locale string, e.g. "en"

     * @throws Exception
     */
    public OpenNLPPosTagger(String locale, String propertyPrefix)
    throws Exception
    {
        super("OpenNLPPosTagger",
                MaryDataType.WORDS,
                MaryDataType.PARTSOFSPEECH,
                MaryUtils.string2locale(locale));
        if (!propertyPrefix.endsWith(".")) propertyPrefix = propertyPrefix + ".";
        this.propertyPrefix = propertyPrefix;
    }

    public void startup() throws Exception
    {
        super.startup();
        
        String modelFile = MaryProperties.needFilename(propertyPrefix+"model");
        String tagdict = MaryProperties.getFilename(propertyPrefix+"tagdict");
        boolean caseSensitive = MaryProperties.getBoolean(propertyPrefix+"tagdict.isCaseSensitive", true);
        String posMapperFile = MaryProperties.getFilename(propertyPrefix+"posMap");

        MaxentModel model = new SuffixSensitiveGISModelReader(new File(modelFile)).getModel();
        if (tagdict != null) {
            TagDictionary dict = new POSDictionary(tagdict,caseSensitive);
            tagger = new POSTaggerME(model, new DefaultPOSContextGenerator(null),dict);
        } else {
            tagger = new POSTaggerME(model, new DefaultPOSContextGenerator(null));
        }
        if (posMapperFile != null) {
            posMapper = new HashMap<String, String>();
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(posMapperFile), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                // skip comments and empty lines
                if (line.startsWith("#") || line.trim().equals("")) continue;
                // Entry format: POS GPOS, i.e. two space-separated entries per line
                StringTokenizer st = new StringTokenizer(line);
                String pos = st.nextToken();
                String gpos = st.nextToken();
                posMapper.put(pos, gpos);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public MaryData process(MaryData d)
    throws Exception
    {
        
        Document doc = d.getDocument(); 
        NodeIterator sentenceIt = MaryDomUtils.createNodeIterator(doc, doc, MaryXML.SENTENCE);
        Element sentence;
        while ((sentence = (Element) sentenceIt.nextNode()) != null) {
            TreeWalker tokenIt = MaryDomUtils.createTreeWalker(sentence, MaryXML.TOKEN);
            List<String> tokens = new ArrayList<String>();
            Element t;
            while ((t = (Element) tokenIt.nextNode()) != null) {
                tokens.add(MaryDomUtils.tokenText(t));
            }
            List<String> partsOfSpeech = null;
            synchronized(this) {
                partsOfSpeech = tagger.tag(tokens);
            }
            tokenIt.setCurrentNode(sentence); // reset treewalker so we can walk through once again
            Iterator<String> posIt = partsOfSpeech.iterator();
            while ((t = (Element) tokenIt.nextNode()) != null) {
                assert posIt.hasNext();
                String pos = posIt.next();
                if (posMapper != null) {
                    String gpos = posMapper.get(pos);
                    if (gpos == null) logger.warn("POS map file incomplete: do not know how to map '"+pos+"'");
                    else pos = gpos;
                }
                t.setAttribute("pos", pos);
            }
        }
        
        MaryData output = new MaryData(outputType(), d.getLocale());
        output.setDocument(doc);
        return output;
    }
    

}

