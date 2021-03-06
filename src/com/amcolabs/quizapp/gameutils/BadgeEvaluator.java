package com.amcolabs.quizapp.gameutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.amcolabs.quizapp.QuizApp;
import com.amcolabs.quizapp.appcontrollers.ProgressiveQuizController.UserAnswer;
import com.amcolabs.quizapp.databaseutils.Badge;
import com.amcolabs.quizapp.databaseutils.Category;
import com.amcolabs.quizapp.databaseutils.GameEvents;
import com.amcolabs.quizapp.databaseutils.GameEvents.EventType;
import com.amcolabs.quizapp.databaseutils.Quiz;
import com.amcolabs.quizapp.databaseutils.QuizPlaySummary;
import com.amcolabs.quizapp.datalisteners.DataInputListener;
import com.amcolabs.quizapp.fileandcommonutils.CommonFunctions;
import com.amcolabs.quizapp.uiutils.UiUtils.UiText;
import com.google.gson.reflect.TypeToken;

public class BadgeEvaluator {

	HashMap<String, Category> mainCategoryList;
	HashMap<String, Quiz> mainQuizList;
	HashMap<String, QuizPlaySummary> mainQuizHistoryList;
 
	QuizApp quizApp;
	ArrayList<ArrayList<String>> categoryList = new ArrayList<ArrayList<String>>();
	ArrayList<ArrayList<String>> quizList = new ArrayList<ArrayList<String>>();
	ArrayList<ArrayList<String>> categoryCombinations = new ArrayList<ArrayList<String>>();;
	
	ArrayList<Integer> categoryFullChildState = new ArrayList<Integer>();
	ArrayList<Integer> quizFullChildState = new ArrayList<Integer>();
	boolean allFlag = false;
	
	public BadgeEvaluator(QuizApp qApp){
		quizApp = qApp;
		
		mainCategoryList = new HashMap<String, Category>();
		mainQuizList = new HashMap<String, Quiz>();
		mainQuizHistoryList = new HashMap<String, QuizPlaySummary>(); 
		
	}
	
	/**
	 * Values must be given as limits i.e., anything, greater than or equal to given value, is counted as True 
	 * -- (0 is used to represent all, it implies condition must apply for all selected)
	 * values must be followed by %x where x is the minimum count for the condition for given type (offlineChallenge and quiz only)
	 * Full child match - must satisfy for all->0 ; atleast one -> 1 ; no restriction(default value) -> -1 
	 * selected quizList will be List of lists which are bound by OR condition
	 * Eg: (offlineChallenge::%0)&&(level::5|10|15) ; (offlineChallenge::id1|id3&id2%2)
	 * @param cond
	 * @param value
	 * @return
	 */
	public void evaluateBadges(HashMap<String, List<UserAnswer>> userAnswersStack, int whoWon){
		initialize(); // To load All quiz history, quizzes and categories
		List<Badge> badges = quizApp.getDataBaseHelper().getAllBadges();
		if(badges==null){
			return;
		}
		List<Badge> pendingBadges = quizApp.getDataBaseHelper().getAllPendingBadges();
		// To remove awarded badges
		List<String> awardedBadges = quizApp.getUser().getBadges();
		ArrayList<String> allBadgeIds = new ArrayList<String>();
		for(int i=0;i<badges.size();i++){
			allBadgeIds.add(badges.get(i).getBadgeId());
		}
		int ind=0;
		if(awardedBadges!=null){
			for(int i=0;i<awardedBadges.size();i++){
				ind = allBadgeIds.indexOf(awardedBadges.get(i));
				badges.remove(ind);
				allBadgeIds.remove(ind);
			}
		}
		if(pendingBadges!=null){
			for(int i=0;i<pendingBadges.size();i++){
				badges.remove(pendingBadges.get(i));
			}
		}
		
		Iterator<Badge> itr = badges.iterator();
		Badge curBadge = null;
		ArrayList<Badge> unlockedBadges = new ArrayList<Badge>();
		String[] ors;
		ArrayList<String> ands;
		String[] cond;
		/**
		 * Determines condition state whether it is True or False
		 */
		boolean andState = true;
		while(itr.hasNext()){
			curBadge = itr.next();
			String condition = curBadge.getCondition();
			reinit();
			
//			String condition = "level::5";
			ors = CommonFunctions.splitString(condition, "||");
			for(int i=0;i<ors.length;i++){
				ands = loadCategoriesAndQuizzes(ors[i]);
				for(int j=0;j<ands.size();j++){
					cond = CommonFunctions.splitString(ands.get(j), "::");
					if(cond.length==2){
						andState = andState && badgeConditionEvaluator(cond[0], cond[1]);
					}
					else{
						System.out.println("Input is not valid, output may not be as expected");
					}
				}
				if(andState){
					unlockedBadges.add(curBadge);
				}
				else{
					andState = true;
				}
			}
		}
		
		// TODO: Yet To implement custom badges
//		ArrayList<Badge> ubadges = evaluateDirectBadges(userAnswersStack,whoWon);
//		if (ubadges!=null && ubadges.size()>0){
//			unlockedBadges.addAll(ubadges);
//		}
		
		if(unlockedBadges.size()>0){
			newBadgeUnlocked(unlockedBadges);
		}
		clearAllData();
	}
	
	private ArrayList<Badge> evaluateDirectBadges(HashMap<String, List<UserAnswer>> userAnswersStack, int whoWon){
		ArrayList<Badge> uBadges = new ArrayList<Badge>();

		// 4 Wrong Answers but Win
		int wrongCount = 0;
		List<UserAnswer> uAns = userAnswersStack.get(quizApp.getUser().uid);
		for(int i=1;i<uAns.size();i++){
			if (uAns.get(i-1).whatUserGot == uAns.get(i).whatUserGot){
				wrongCount= wrongCount + 1;
			}
		}
		if (wrongCount>3 && whoWon==Quiz.WON){
			uBadges.add(quizApp.getDataBaseHelper().getBadgeById("d_b1"));
		}
		
		
		
		
		//quizApp.getDataBaseHelper().getAllQuizSummary();
		
		return uBadges;
	}
	
	private void initialize() {
		List<Category> tmp = quizApp.getDataBaseHelper().getAllCategories();
		if(tmp!=null){
			for(int i=0;i<tmp.size();i++){
				mainCategoryList.put(tmp.get(i).categoryId, tmp.get(i));
			}
		}
		
		List<Quiz> tmp1 = quizApp.getDataBaseHelper().getAllQuizzes(null);
		if(tmp1!=null){
			for(int i=0;i<tmp1.size();i++){
				mainQuizList.put(tmp1.get(i).quizId, tmp1.get(i));
			}
		}

		List<QuizPlaySummary> tmp2 = quizApp.getDataBaseHelper().getAllQuizSummary();

		if(tmp2!=null){
			for(int i=0;i<tmp2.size();i++){
				mainQuizHistoryList.put(tmp2.get(i).getQuizId(), tmp2.get(i));
			}
		}
	}

	private void reinit(){
		categoryList.clear();
//		categoryList.add(new ArrayList<String>());
		quizList.clear();
//		quizList.add(new ArrayList<String>());
		categoryFullChildState.clear();
		quizFullChildState.clear();
		categoryCombinations.clear();
	}

	public void newBadgeUnlocked(final ArrayList<Badge> unlockedBadges) {
		if(unlockedBadges==null || unlockedBadges.size()==0)
			return;
		ArrayList<String> badgeIds = new ArrayList<String>();
		for(int i=0;i<unlockedBadges.size();i++){
			Badge badge = unlockedBadges.get(i);
			if(badge==null){
				//TODO: fix this badge thing , whis shoiuld not be happening
				continue;
			}
			String badgeId = badge.getBadgeId();
			badgeIds.add(badgeId);
			quizApp.getDataBaseHelper().addGameEvents(new GameEvents(EventType.UNLOCKED_BADGE, badgeId, null, null));
		}
		quizApp.getServerCalls().addBadges(badgeIds, new DataInputListener<Boolean>(){
			@Override
			public String onData(Boolean s) {
				if (s){
					if(quizApp.getUser().getBadges()==null){
						quizApp.getUser().setBadges(new ArrayList<String>());
					}
					for(int i=0;i<unlockedBadges.size();i++){
						quizApp.getUser().getBadges().add(unlockedBadges.get(i).getBadgeId()); // TODO: Check for crash here
						quizApp.getStaticPopupDialogBoxes().showUnlockedBadge(unlockedBadges.get(i),false, UiText.NEW_BADGE_UNLOCKED_MESSAGE.getValue());
					}
					if(!quizApp.getDataBaseHelper().removePendingState(unlockedBadges)){
						System.out.println("DB update error");
					}
				}
				else{
					if(!quizApp.getDataBaseHelper().setPendingState(unlockedBadges)){
//						new Exception("DB data update error");
						System.out.println("DB update error");
					}
				}
				return null;
			}
		});
	}

	/**
	 * Category and Quiz can be manipulated to give conditions. offlineChallenge
	 * default -1 -> no restrictions(one quiz match with other conditions); 0 -> must match all given entries fully; n -> must match n entries 
	 * @param condition
	 */
	private ArrayList<String> loadCategoriesAndQuizzes(String condition){
//		for(int i=0;i<ors.length;i++){
//			quizList.add(new ArrayList<Quiz>());
//		}
		String[] ands;
		String[] cond;
//		String[] tmp;
//		String[] childConditions;
//		String[] cList;
//		ArrayList<String> tmp1;
//		ArrayList<String> tmp2;
//		int sz;
		ands = CommonFunctions.splitString(condition, "&&");
		cond = CommonFunctions.splitString(ands[0], "::");
		if(cond[0].equalsIgnoreCase("offlineChallenge")){
			if(cond.length>1){
				loadCategoryList(cond[1]);
			}
			if(ands.length>1){
				cond = CommonFunctions.splitString(ands[1],"::");
				if(cond[0].equalsIgnoreCase("quiz")){
					if(cond.length>1){
						loadQuizList(cond[1]);
					}
					return getRemainingElements(2,ands);
				}
				else{
					return getRemainingElements(1,ands);
				}
			}
			System.out.println("Something is not right");
			return getRemainingElements(1,ands);
		}
		else if(cond[0].equalsIgnoreCase("quiz")){
			if(cond.length>1){
				loadQuizList(cond[1]);
			}
			return getRemainingElements(1,ands);
		}
		else{
			loadAllQuizzes();
			return getRemainingElements(0,ands);
		}
	}
	
	private void loadCategoryList(String cond){
		String[] childConditions = CommonFunctions.splitString(cond, "|");
		String[] cList;
		String[] tmp;
		ArrayList<String> tmp1;
		for(int i=0;i<childConditions.length;i++){
			cList = CommonFunctions.splitString(childConditions[i], "%");
			if (cList[0].equalsIgnoreCase("")){
				categoryList.add(new ArrayList<String>(mainCategoryList.keySet()));
			}
			else{
				tmp = CommonFunctions.splitString(cList[0],"&");
				tmp1 = new ArrayList<String>();
				for(int j=0;j<tmp.length;j++){
					tmp1.add(tmp[j]);
				}
				categoryList.add(tmp1);
			}
			if(cList.length>1){
				categoryFullChildState.add(Integer.valueOf(cList[1]));
			}
			else{
				categoryFullChildState.add(-1);
			}
		}
	}
	
	private void loadQuizList(String cond){
		String[] childConditions = CommonFunctions.splitString(cond, "|");
		String[] cList;
		String[] tmp;
		ArrayList<String> tmp1;
		for(int i=0;i<childConditions.length;i++){
			cList = CommonFunctions.splitString(childConditions[i], "%");
			if (cList[0].equalsIgnoreCase("")){
				quizList.add(new ArrayList<String>(mainQuizList.keySet()));
			}
			else{
				tmp = CommonFunctions.splitString(cList[0],"&");
				tmp1 = new ArrayList<String>();
				for(int j=0;j<tmp.length;j++){
					tmp1.add(tmp[j]);
				}
				quizList.add(tmp1);
			}
			if(cList.length>1){
				quizFullChildState.add(Integer.valueOf(cList[1]));
			}
			else{
				quizFullChildState.add(-1);
			}
		}
	}
	
	private ArrayList<String> getRemainingElements(int start,String[] array) {
		ArrayList<String> tmp1 = new ArrayList<String>();
		for(int i=start;i<array.length;i++){
			tmp1.add(array[i]);
		}
		return tmp1;
	}
	
	/**
	 * Basic Evaluator for condition
	 * @param cond Eg: Category
	 * @param value Eg: "catId1"|"catId2"&"catId3"
	 * @return
	 */
	public boolean badgeConditionEvaluator(String cond,String value){
		// To be implemented
		boolean andState = true;
		String[] ands = null;
		String[] ors = null;
		String val = null;
		ors = CommonFunctions.splitString(value,"|");
		for(int i=0;i<ors.length;i++){
			ands = CommonFunctions.splitString(ors[i],"&");
			for(int j=0;j<ands.length;j++){
				val = ands[j];
				andState = andState && basicConditionEvaluator(cond,val,i);
			}
			if(andState){
				return true;
			}
			else{
				andState = true;
			}
		}
		return false;
	}
	
	private boolean basicConditionEvaluator(String cond,String value,int index){
		boolean state = false;

		if(cond.equalsIgnoreCase("level")){
			if(categoryFullChildState.get(index)<0){
				if(quizFullChildState.get(index)<0){
					return matchQuizListLevel(index, 1, value);
				}
				else if(quizFullChildState.get(index)==0){
					return matchQuizListLevel(quizList.get(index), value);
				}
			}
			else if(categoryFullChildState.get(index)==0){
				return matchQuizListLevel(getQuizIdsOfCategories(categoryList.get(index)), value);
			}
			else{
				categoryCombinations=CommonFunctions.getCombinations(categoryList.get(index), categoryFullChildState.get(index), 0, new ArrayList<String>(),categoryCombinations);
				for(int i=0;i<categoryCombinations.size();i++){
					state = matchQuizListLevel(getQuizIdsOfCategories(categoryCombinations.get(i)), value);
					if(state)
						return true;
				}
			}
		}
		else if(cond.equalsIgnoreCase("streak")){
			if(categoryFullChildState.get(index)<0){
				if(quizFullChildState.get(index)<0){
					return matchQuizListStreak(index, 1, value);
				}
				else if(quizFullChildState.get(index)==0){
					return matchQuizListStreak(quizList.get(index), value);
				}
			}
			else if(categoryFullChildState.get(index)==0){
				return matchQuizListStreak(getQuizIdsOfCategories(categoryList.get(index)), value);
			}
			else{
				categoryCombinations=CommonFunctions.getCombinations(categoryList.get(index), categoryFullChildState.get(index), 0, new ArrayList<String>(),new ArrayList<ArrayList<String>>());
				for(int i=0;i<categoryCombinations.size();i++){
					state = matchQuizListStreak(getQuizIdsOfCategories(categoryCombinations.get(i)), value);
					if(state)
						return true;
				}
			}
		}
		else if(cond.equalsIgnoreCase("quizCount")){
			if(categoryFullChildState.get(index)<0){
				if(quizFullChildState.get(index)<0){
					return matchQuizListQuizCount(index, 1, value);
				}
				else if(quizFullChildState.get(index)==0){
					return matchQuizListQuizCount(quizList.get(index), value);
				}
			}
			else if(categoryFullChildState.get(index)==0){
				return matchQuizListQuizCount(getQuizIdsOfCategories(categoryList.get(index)), value);
			}
			else{
				categoryCombinations=CommonFunctions.getCombinations(categoryList.get(index), categoryFullChildState.get(index), 0, new ArrayList<String>(),new ArrayList<ArrayList<String>>());
				for(int i=0;i<categoryCombinations.size();i++){
					state = matchQuizListQuizCount(getQuizIdsOfCategories(categoryCombinations.get(i)), value);
					if(state)
						return true;
				}
			}
		}
		else if(cond.equalsIgnoreCase("totalQuizCount")){
			if(categoryFullChildState.get(index)<0){
				if(quizFullChildState.get(index)<0){
					return matchQuizListTotalQuizCount(quizList.get(index), value);
				}
			}
			else if(categoryFullChildState.get(index)==0){
				System.out.println("Not valid for TotalCount, recheck your condition");
			}
			else{
				categoryCombinations=CommonFunctions.getCombinations(categoryList.get(index), categoryFullChildState.get(index), 0, new ArrayList<String>(),new ArrayList<ArrayList<String>>());
				for(int i=0;i<categoryCombinations.size();i++){
					state = matchQuizListTotalQuizCount(getQuizIdsOfCategories(categoryCombinations.get(i)), value);
					if(state)
						return true;
				}
			}
		}
		return state;
	}

	private ArrayList<String> getQuizIdsOfCategories(ArrayList<String> categoryIds){
		ArrayList<String> tmp = new ArrayList<String>();
		for(int i=0;i<categoryIds.size();i++){
			List<String> qlist = quizApp.getConfig().getGson().fromJson(mainCategoryList.get(categoryIds.get(i)).quizList, new TypeToken<List<String>>(){}.getType());
			tmp.addAll(qlist);
		}
		return tmp;
	}
	
	private boolean matchQuizListLevel(int index,int count,String value){
		int tmp_count = 0;
		for(int i=0;i<quizList.get(index).size();i++){
			if(Double.valueOf(value)<=GameUtils.getLevelFromXp(quizApp.getUser().getPoints(quizList.get(index).get(i))))
				tmp_count++;
			if(tmp_count>=count){
				return true;
			}
		}
		return false;
	}
	
	private boolean matchQuizListLevel(ArrayList<String> quizIds,String value){
		for(int i=0;i<quizIds.size();i++){
			if(Double.valueOf(value)>GameUtils.getLevelFromXp(quizApp.getUser().getPoints(quizIds.get(i))))
				return false;
		}
		return true;
	}
	
	private boolean matchQuizListStreak(int index,int count,String value){
		int tmp_count = 0;

		QuizPlaySummary qh;
		for(int i=0;i<quizList.get(index).size();i++){
			qh = mainQuizHistoryList.get(quizList.get(index).get(i));
			if(qh==null)
				continue;
			if(Integer.valueOf(value)<=qh.getStreak())
				tmp_count++;
			if(tmp_count>=count){
				return true;
			}
		}
		return false;
	}
	
	private boolean matchQuizListStreak(ArrayList<String> quizIds,String value){
		QuizPlaySummary qh;
		for(int i=0;i<quizIds.size();i++){
			qh = mainQuizHistoryList.get(quizIds.get(i));
			if(qh==null || Integer.valueOf(value)>qh.getStreak())
				return false;
		}
		return true;
	}

	private boolean matchQuizListQuizCount(int index,int count,String value){
		int tmp_count = 0;
		QuizPlaySummary qh;

		for(int i=0;i<quizList.get(index).size();i++){
			qh = mainQuizHistoryList.get(quizList.get(index).get(i));
			if(qh==null)
				continue;
			if(Integer.valueOf(value)<=qh.getTotalCount())
				tmp_count++;
			if(tmp_count>=count){
				return true;
			}
		}
		return false;
	}
	
	private boolean matchQuizListQuizCount(ArrayList<String> quizIds,String value){
		QuizPlaySummary qh;

		for(int i=0;i<quizIds.size();i++){
			qh = mainQuizHistoryList.get(quizIds.get(i));
			if(qh==null || Integer.valueOf(value)>qh.getTotalCount())
				return false;
		}
		return true;
	}
	
	private boolean matchQuizListTotalQuizCount(ArrayList<String> quizIds, String value) {
		QuizPlaySummary qh;

		int totalCount = 0;
		for(int i=0;i<quizIds.size();i++){
			qh = mainQuizHistoryList.get(quizIds.get(i));
			if(qh==null)
				continue;
			totalCount = totalCount + qh.getTotalCount();
			if(Integer.valueOf(value)<=totalCount)
				return true;
		}
		return false;
	}
	
	private void loadAllQuizzes(){
		categoryList.add(new ArrayList<String>(mainCategoryList.keySet()));
		quizList.add(new ArrayList<String>(mainQuizList.keySet()));
		categoryFullChildState.add(-1);
		quizFullChildState.add(-1);
	}
	
	private void clearAllData(){
		mainCategoryList.clear();
		mainQuizList.clear();
		mainQuizHistoryList.clear();
		reinit();
	}
}
