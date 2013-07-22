/**
* Copyright 2013 IPSoft Inc.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
*   
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.googlecode.clearnlp.component.dep;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import com.googlecode.clearnlp.classification.feature.JointFtrXml;
import com.googlecode.clearnlp.classification.model.StringModel;
import com.googlecode.clearnlp.classification.prediction.StringPrediction;
import com.googlecode.clearnlp.classification.train.StringTrainSpace;
import com.googlecode.clearnlp.constant.english.ENAux;
import com.googlecode.clearnlp.constituent.CTLibEn;
import com.googlecode.clearnlp.dependency.DEPLabel;
import com.googlecode.clearnlp.dependency.DEPLib;
import com.googlecode.clearnlp.dependency.DEPLibEn;
import com.googlecode.clearnlp.dependency.DEPNode;
import com.googlecode.clearnlp.morphology.MPLibEn;

/**
 * Dependency parser using selectional branching.
 * @since 1.3.2
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
public class EnglishDEPParser extends AbstractDEPParser
{
//	====================================== CONSTRUCTORS ======================================
	
	/** Constructs a dependency parsing for training. */
	public EnglishDEPParser(JointFtrXml[] xmls, StringTrainSpace[] spaces, Object[] lexica, double margin, int beams)
	{
		super(xmls, spaces, lexica, margin, beams);
	}
	
	/** Constructs a dependency parsing for developing. */
	public EnglishDEPParser(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, double margin, int beams)
	{
		super(xmls, models, lexica, margin, beams);
	}
	
	/** Constructs a dependency parser for bootsrapping. */
	public EnglishDEPParser(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, double margin, int beams)
	{
		super(xmls, spaces, models, lexica, margin, beams);
	}
	
	/** Constructs a dependency parser for decoding. */
	public EnglishDEPParser(ZipInputStream in)
	{
		super(in);
	}
	
//	================================ RE-RANKING ================================
	
	@Override
	protected void rerankPredictions(List<StringPrediction> ps)
	{
		DEPNode lambda = d_tree.get(i_lambda);
		DEPNode beta   = d_tree.get(i_beta);
		
		int i, size = ps.size(), count = 0;
		boolean lChanged, gChanged = false;
		StringPrediction prediction;
		DEPLabel label;
		
		for (i=0; i<size; i++)
		{
			lChanged = false;
			prediction = ps.get(i);
			label = new DEPLabel(prediction.label, prediction.score);
			
			if (label.isArc(LB_LEFT))
			{
				if (rerankUnique(prediction, label, beta, DEPLibEn.P_SBJ, i_lambda+1, i_beta))
					lChanged = true;
				else if (rerankNonHead(prediction, beta))
					lChanged = true;
			}
			else if (label.isArc(LB_RIGHT))
			{
				if (rerankUnique(prediction, label, lambda, DEPLibEn.P_SBJ, 1, i_beta))
					lChanged = true;
				else if (rerankNonHead(prediction, lambda))
					lChanged = true;
			}
			
			if (lChanged)
				gChanged = true;
			else
			{
				count++;
				if (count >= 2) break;
			}
		}
		
		if (gChanged) Collections.sort(ps);
	}
	
	private boolean rerankUnique(StringPrediction prediction, DEPLabel label, DEPNode head, Pattern p, int bIdx, int eIdx)
	{
		if (p.matcher(label.deprel).find())
		{
			DEPNode node;
			int i;
			
			for (i=bIdx; i<eIdx; i++)
			{
				node = d_tree.get(i);
				
				if (node.isDependentOf(head) && p.matcher(node.getLabel()).find())
				{
					prediction.score = -1;
					return true;
				}
			}
		}
		
		return false;
	}
	
	private boolean rerankNonHead(StringPrediction prediction, DEPNode head)
	{
		if (isNotHead(head))
		{
			prediction.score = -1;
			return true;
		}
		
		return false;
	}
	
//	================================ POST PARSE ================================
	
	@Override
	protected void postParse()
	{
		DEPNode node;
		int i;
		
		for (i=1; i<t_size; i++)
		{
			node = d_tree.get(i);
			postParsePP(node);
		}
	}
	
	private boolean postParsePP(DEPNode node)
	{
		DEPNode head = node.getHead();
		if (head == null)	return false;
		
		DEPNode gHead = head.getHead();
		if (gHead == null)	return false;
		
		DEPNode ggHead = gHead.getHead();
		if (ggHead == null)	return false;
		
		if (node.id < ggHead.id && ggHead.id < gHead.id && gHead.id < head.id && head.isPos(CTLibEn.POS_IN) && MPLibEn.isNoun(gHead.pos) && MPLibEn.isVerb(ggHead.pos))
		{
			head.setHead(ggHead);
			return true;
		}
		
		return false;
	}
	
//	================================ RESET POST ================================
	
	@Override
	protected void resetPost(DEPNode lambda, DEPNode beta, DEPLabel label)
	{
		if (lambda.isDependentOf(beta))
		{
			resetVerbPOSTag(beta, lambda);
			resetNotHead(lambda);
		}
		else if (beta.isDependentOf(lambda))
		{
			resetVerbPOSTag(lambda, beta);
		}
	}

	private void resetVerbPOSTag(DEPNode head, DEPNode dep)
	{
		String p2 = head.getFeat(DEPLib.FEAT_POS2);
		
		if (p2 != null && (MPLibEn.isNoun(head.pos) || head.isPos(CTLibEn.POS_IN)) && ((MPLibEn.isVerb(p2) || p2.equals(CTLibEn.POS_UH))))
		{
			if (dep.isLabel(DEPLibEn.DEP_DOBJ) || DEPLibEn.isAuxiliary(dep.getLabel()) || dep.isLabel(DEPLibEn.DEP_PRT) || dep.isLabel(DEPLibEn.DEP_ACOMP))// || DEPLibEn.isSubject(dep.getLabel()) || dep.equals(DEPLibEn.DEP_EXPL)) || dep.isLabel(DEPLibEn.DEP_AGENT) || dep.isLabel(DEPLibEn.DEP_ATTR) || dep.isLabel(DEPLibEn.DEP_IOBJ)))
			{
				if (p2.equals(CTLibEn.POS_UH))
					head.addFeat(DEPLib.FEAT_POS2, CTLibEn.POS_VB);
				
				n_2ndPos[head.id] += 1d;
			}
		}
	}
	
	private boolean resetNotHead(DEPNode lambda)
	{
		if (isNotHead(lambda) && clearPreviousDependents(lambda))
		{
			i_lambda = lambda.id;
			passAux();
			return true;
		}
		
		return false;
	}
	
	@Override
	protected boolean isNotHead(DEPNode node)
	{
		String label = node.getLabel();
		return label != null && DEPLibEn.isAuxiliary(label);
	}
	
//	================================ RESET PRE ================================
	
	@Override
	protected boolean resetPre(DEPNode lambda, DEPNode beta)
	{
		int idx = resetBeVerb(lambda, beta);
		
		if (idx > 0)
		{
			i_lambda = idx - 1;
			d_score += 100;
			return true;
		}
		
		return false;
	}
	
	private int resetBeVerb(DEPNode lambda, DEPNode beta)
	{
		DEPNode beVerb = lambda.getHead();
		String subj = lambda.getLabel();
		int vType = 0;
		
		if (beta.isPos(CTLibEn.POS_VBN))
			vType = 1;
		else if (beta.isPos(CTLibEn.POS_VBG))
			vType = 2;
		else if (beta.isPos(CTLibEn.POS_VBD))
		{
			String p2 = beta.getFeat(DEPLib.FEAT_POS2);
			if (p2 != null && p2.equals(CTLibEn.POS_VBN))	vType = 1;
		}
		
		if (vType > 0 && subj != null && (DEPLibEn.isSubject(subj) || (subj.equals(DEPLibEn.DEP_ATTR) && hasNoDependent(beVerb, 1, i_beta))))
		{
			DEPNode gHead = beVerb.getHead();
			
			// be - subj(lambda) -  vb[ng](beta)
			if (beVerb.isLemma(ENAux.BE) && beVerb.id < lambda.id && (gHead == null || gHead.id < beVerb.id))
			{
				DEPNode node;
				int i;
				
				for (i=beVerb.id+1; i<i_beta; i++)
				{
					node = d_tree.get(i);
					
					if (node.isDependentOf(beVerb))
						node.setHead(beta);
				}
				
				clearPreviousDependents(beVerb);
				beVerb.setHead(beta);
				
				if (vType == 1)
				{
					beVerb.setLabel(DEPLibEn.DEP_AUXPASS);
					
					if (subj.equals(DEPLibEn.DEP_NSUBJ) || subj.equals(DEPLibEn.DEP_ATTR))
						lambda.setLabel(DEPLibEn.DEP_NSUBJPASS);
					else if (subj.equals(DEPLibEn.DEP_CSUBJ))
						lambda.setLabel(DEPLibEn.DEP_CSUBJPASS);
				}
				else
				{
					beVerb.setLabel(DEPLibEn.DEP_AUX);
				}
				
				if (beta.isPos(CTLibEn.POS_VBD))
					beta.pos = CTLibEn.POS_VBN;
				
				return beVerb.id;
			}
		}
		
		return -1;
	}
	
	private boolean hasNoDependent(DEPNode head, int bIdx, int eIdx)
	{
		DEPNode node;
		int i;
		
		for (i=bIdx; i<eIdx; i++)
		{
			node = d_tree.get(i);
			
			if (node.isDependentOf(head))
				return false;
		}
		
		return true;
	}
	
	private boolean clearPreviousDependents(DEPNode head)
	{
		boolean found = false;
		DEPNode node;
		int i;
		
		for (i=head.id-1; i>0; i--)
		{
			node = d_tree.get(i);
			
			if (node.isDependentOf(head))
			{
				node.clearHead();
				s_reduce.remove(node.id);
				found = true;
			}
		}
		
		return found;
	}
}