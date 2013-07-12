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
package com.googlecode.clearnlp.generation;


import java.util.List;

import com.googlecode.clearnlp.constant.english.ENModal;
import com.googlecode.clearnlp.constant.english.STConstant;
import com.googlecode.clearnlp.constituent.CTLibEn;
import com.googlecode.clearnlp.dependency.DEPArc;
import com.googlecode.clearnlp.dependency.DEPLibEn;
import com.googlecode.clearnlp.dependency.DEPNode;
import com.googlecode.clearnlp.dependency.DEPTree;
import com.googlecode.clearnlp.morphology.MPLibEn;

/**
 * @since 1.4.0
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
public class LGLibEn
{
	/** 
	 * Converts all forms of "you" to "I" and vice versa.
	 * PRE: {@link DEPTree#setDependents()} is called.
	 */
	static public void convertUnI(DEPTree tree)
	{
		int i, size = tree.size();
		DEPNode node, head;
		
		for (i=1; i<size; i++)
		{
			node  = tree.get(i);
			head  = node.getHead();
			
			if (node.isLemma("I"))
			{
				node.form = node.lemma = "you";
				
				if (!convertI2UBe(head))
				{
					for (DEPArc arc : head.getDependents())
					{
						if (arc.isLabel(DEPLibEn.P_AUX) && convertI2UBe(arc.getNode()))
							break;
					}					
				}
			}
			else if (node.isLemma("you"))
			{
				if ((node.isLabel(DEPLibEn.P_SBJ) && !DEPLibEn.isSmallClauseAux(head))
				||  (node.isLabel(DEPLibEn.DEP_CONJ) && head.isLabel(DEPLibEn.P_SBJ) && !DEPLibEn.isSmallClauseAux(head.getHead())))
				{
					node.form = node.lemma = "I";
					
					if (!node.isLabel(DEPLibEn.DEP_CONJ) && !node.containsDependent(DEPLibEn.DEP_CONJ))
					{
						if (!convertU2IBe(head))
						{
							for (DEPArc arc : head.getDependents())
							{
								if (arc.isLabel(DEPLibEn.P_AUX) && convertU2IBe(arc.getNode()))
									break;
							}
						}
					}
				}
				else
					node.form = node.lemma = "me";
			}
			else if (node.isLemma("my"))		node.form = node.lemma = "your";
			else if (node.isLemma("me"))		node.form = node.lemma = "you";
			else if (node.isLemma("mine"))		node.form = node.lemma = "yours";
			else if (node.isLemma("myself"))	node.form = node.lemma = "yourself";
			else if (node.isLemma("your"))		node.form = node.lemma = "my";
			else if (node.isLemma("yours"))		node.form = node.lemma = "mine";
			else if (node.isLemma("yourself"))	node.form = node.lemma = "myself";
		}
	}
	
	static private boolean convertI2UBe(DEPNode node)
	{
		if (node.form.equalsIgnoreCase("am"))
		{
			node.form = "are";
			return true;
		}
		else if (node.form.equalsIgnoreCase("was"))
		{
			node.form = "were";
			return true;
		}
		
		return false;
	}
	
	static private boolean convertU2IBe(DEPNode head)
	{
		if (head.form.equalsIgnoreCase("are"))
		{
			head.form = "am";
			return true;
		}
		else if (head.form.equalsIgnoreCase("were"))
		{
			head.form = "was";
			return true;
		}
		
		return false;
	}
	
	/** @return the possessive form of the specific form (e.g., John -> John's). */
	static public String getPossessiveForm(String nounForm)
	{
		String suffix = nounForm.endsWith("s") ? "'" : "'s";
		return nounForm + suffix;
	}
	
	/** PRE: {@link DEPTree#setDependents()} is called. */
	static public String getForms(DEPNode root, String delim)
	{
		StringBuilder build = new StringBuilder();
		String s;
		
		getSubFormsAux(build, root, null, delim);
		s = build.toString();
		
		if (s.startsWith(delim))
			s = s.substring(delim.length());
		
		return s;
	}
	
	/** Called by {@link LGLibEn#getForms(DEPNode, String)}. */
	static private void getSubFormsAux(StringBuilder build, DEPNode node, DEPNode prev, String delim)
	{
		List<DEPArc> deps = node.getDependents();
		int i, size = deps.size();
		boolean notAdded = true;
		DEPNode curr;
		
		for (i=0; i<size; i++)
		{
			curr = deps.get(i).getNode();
			
			if (notAdded && curr.id > node.id)
			{
				prev = addForm(build, node, prev, delim);
				notAdded = false;
			}
			
			if (curr.getDependents().isEmpty())
				prev = addForm(build, curr, prev, delim);
			else
				getSubFormsAux(build, curr, prev, delim);
		}
		
		if (notAdded)
			prev = addForm(build, node, prev, delim);
	}
	
	/** Called by {@link LGLibEn#getSubFormsAux(StringBuilder, DEPNode, String)}. */
	static private DEPNode addForm(StringBuilder build, DEPNode curr, DEPNode prev, String delim)
	{
		if (!curr.isForm(STConstant.EMPTY))
		{
			if (!attachLeft(curr, prev)) build.append(delim);
			String coref = getReferentValueOf3rdPronoun(curr);
			
			if (coref != null)		build.append(coref);
			else 					build.append(curr.form);			
		}
		
		return curr;
	}
	
	/** Called by {@link DEPNode#getSubFormsAux(DEPNode, String, StringBuilder)} */
	static private boolean attachLeft(DEPNode curr, DEPNode prev)
	{
		String lower = curr.form.toLowerCase();
		
		if (lower.startsWith("'"))
		{
			if (curr.isPos(CTLibEn.POS_POS))
				return true;
			
			if (MPLibEn.isVerb(curr.pos) || curr.isPos(CTLibEn.POS_MD) || curr.isPos(CTLibEn.POS_RQ) || curr.isLabel(DEPLibEn.DEP_NEG))
				return true;
		}
		else if (lower.equals("n't"))
			return true;
		else if (lower.equals("not"))
		{
			if (prev.isLemma(ENModal.CAN))
			{
				curr.form = "'t";
				return true;
			}
			
			String pLower = prev.form.toLowerCase();
			
			if (pLower.equals(STConstant.DO) || pLower.equals(STConstant.DOES) || pLower.equals(STConstant.DID) || prev.isLemma(ENModal.COULD) || prev.isLemma(ENModal.SHOULD) || prev.isLemma(ENModal.WOULD))
			{
				curr.form = "n't";
				return true;
			}
		}
		else if (curr.isPos(CTLibEn.POS_HYPH) || curr.isPos(CTLibEn.POS_COLON) || curr.isPos(CTLibEn.POS_COMMA) || curr.isPos(CTLibEn.POS_PERIOD) || curr.isPos(CTLibEn.POS_RQ) || curr.isPos(CTLibEn.POS_RRB))
			return true;
		else if (prev != null && (prev.isPos(CTLibEn.POS_HYPH) || prev.isPos(CTLibEn.POS_LQ) || prev.isPos(CTLibEn.POS_LRB)))
			return true;
		
		return false;
	}
	
	static public String getReferentValueOf3rdPronoun(DEPNode node)
	{
		String coref = node.getFeat(DEPLibEn.FEAT_COREF);
		if (coref == null)	return null;
		
		if (node.isLemma("his") || node.isLemma("hers") || node.isLemma("its") || node.lemma.startsWith("our") || node.lemma.startsWith("your") || node.lemma.startsWith("their"))
			return getPossessiveForm(coref);
		
		if (node.isLemma("he") || node.isLemma("him") || node.isLemma("she") || node.isLemma("it") || node.isLemma("we") || node.isLemma("us") || node.isLemma("they") || node.isLemma("them"))
			return coref;
		
		if (node.isLemma("her"))
			return node.isPos(CTLibEn.POS_PRPS) ? getPossessiveForm(coref) : coref;
		
		return null;
	}
}
