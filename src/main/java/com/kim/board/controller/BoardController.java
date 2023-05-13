package com.kim.board.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.kim.board.biz.board.BoardService;
import com.kim.board.biz.board.BoardVO;
import com.kim.board.biz.common.Crawling;
import com.kim.board.biz.related.RelatedService;
import com.kim.board.biz.related.RelatedVO;
import com.kim.board.biz.word.WordService;
import com.kim.board.biz.word.WordVO;

@Controller
public class BoardController {
	@Autowired
	private BoardService boardService;
	@Autowired
	private RelatedService relatedService;
	@Autowired
	private WordService wordService;
	@Autowired
	private Crawling crawling;

	// 게시판 목록 페이지 진입
	@RequestMapping(value = "/boardView.do")
	public String boardView(BoardVO bvo, Model model, HttpServletRequest request) {
		System.out.println("boardView.do 진입");
		List<BoardVO> datas = boardService.selectAll(bvo);
		System.out.println();
		System.out.println("-----------------------------------------------------");
		System.out.println("게시글 개수: " + datas.size()); // 상품 개수 로그
		if (datas.size() < 10) { // 현재 게시글이 25개 미만이면
			datas = crawling.sample(request); // 크롤링을 하고
			for(BoardVO v: datas) {
				insertBoard(v, model); // 크롤링한 데이터로 게시글 작성하기 과정을 수행
			}
		}
		else {
			System.out.println("게시물 이미 생성됨");
		}
		model.addAttribute("boardNum", bvo.getBoardNum());
		return "board.jsp";
	}

	// 게시글 상세보기 페이지 진입
	@RequestMapping(value = "/boardDetailView.do")
	public String boardDetailView(BoardVO bvo, Model model) {
		System.out.println("boardDetailView.do 진입");
		System.out.println("bvo.boardNum: " + bvo.getBoardNum());
		BoardVO preBvo = boardService.selectOne(bvo);
		model.addAttribute("board", preBvo);
		return "board_detail.jsp";
	}

	// 게시글 작성하기 페이지 진입
	@RequestMapping(value = "/insertBoardView.do")
	public String insertBoardView(HttpSession session, HttpServletResponse response) {
		System.out.println("insertBoardView.do 진입");
		return "board_write.jsp";
	}

	// 게시글 작성하기 페이지에서
	// 게시글 작성(insert) 수행 및 해당 글 상세 보기(작성 결과 보기) 페이지로 이동
	@RequestMapping(value = "/insertBoard.do")
	public String insertBoard(BoardVO bvo, Model model) {
		System.out.println("insertBoard.do 진입");
		System.out.println("bvo: " + bvo);
		// 1. 전체 게시글 목록 가져오고 새 게시글 저장
		System.out.println("inserBoard.do step 1-1");
		List<BoardVO> allBoardList = boardService.selectAll(bvo);
		boardService.insert(bvo);
		bvo.setBoardSearchCondition("last");
		BoardVO preBvo = boardService.selectOne(bvo);
		System.out.println("저장한 게시글 preBvo: " + preBvo);
		int preNum = preBvo.getBoardNum();
		// 연관 게시물 탐색
			// 1-2. 게시글 내용을 공백으로 구분하여 wordArray 배열에 담기
			System.out.println("inserBoard.do step 1-2");
			String[] wordArray = bvo.getBoardContent().split(" ");
			// 1-3. wordArray를 WORD 테이블에 갱신하기 위해 중복 제거하기
			System.out.println("inserBoard.do step 1-3");
			// 중복 제거를 위해 HashSet 사용
	        HashSet<String> uniqueWords = new HashSet<String>(Arrays.asList(wordArray));
	        // 중복 제거된 문자열 배열 생성
	        String[] uniqueArray = uniqueWords.toArray(new String[uniqueWords.size()]);
	        // 문자열의 끝에서 조사와 문장 부호를 제거하기
	        for(int n=0; n<uniqueArray.length; n++) {
	            // 세 글면 마지막 글자 가져와서 처리
	            if(uniqueArray[n].length() == 3) {
	            	String lastChar = uniqueArray[n].substring(uniqueArray[n].length() - 1);
	            	if(lastChar.matches("은|는|이|가|을|를|에|로|의|\\,|\\.|\\!|\\?|\\:")) {
	            		uniqueArray[n] = uniqueArray[n].substring(0, uniqueArray[n].length() - 1);
	            	}
	            }
	            // 네 글자 이상이면 마지막 두 글자 가져와서 처리
	            else if(uniqueArray[n].length() > 3) {
		        	String lastChar = uniqueArray[n].substring(uniqueArray[n].length() - 1);
		        	String lastTwoChars = uniqueArray[n].substring(uniqueArray[n].length() - 2);
	            	if(lastChar.matches("은|는|이|가|을|를|에|로|의|\\,|\\.|\\!|\\?|\\:")) {
	            		uniqueArray[n] = uniqueArray[n].substring(0, uniqueArray[n].length() - 1);
	            	}
	            	else if(lastTwoChars.matches("에서|으로|부터|라고|까지|하고|하여|하지|")) {
	            		uniqueArray[n] = uniqueArray[n].substring(0, uniqueArray[n].length() - 2);
	            	}
	            }
	        }
			// 순회하며 word.update 수행(w_found, w_ratio ++)
			WordVO wvo = new WordVO();
			for(String v : uniqueArray) {
				wvo.setWordWord(v);
				wordService.update(wvo);
			}
			// 1-4. wordArray 배열을 순회하며 해당 단어가 몇 번 나오는지 알아내기
			System.out.println("inserBoard.do step 1-4");
			ArrayList<WordVO> preWordVOList = new ArrayList<WordVO>();
			for(int i=0; i<wordArray.length; i++) {
				int wordFound=0;
				// preWordVOList에 아직 없는 단어라면
				boolean foundFlag = false;
				for(WordVO v : preWordVOList) {
					if(wordArray[i].equals(v.getWordWord())){
						foundFlag = true;
					}
				}
				if(!foundFlag) {
					WordVO wvo2 = new WordVO();
					for(int a=0; a<wordArray.length; a++) {
						wvo2.setWordWord(wordArray[i]);
						if(wordArray[i].equals(wordArray[a])) {
							wordFound++;
							System.out.println("pre 내부 일치하는 단어 발견");
						}
					}
					// 1-5. 단어별로 w_percentage < 40 면 단어, 횟수, 비중을 wordVO에 담기 -> preWordVOList에 담기
					System.out.println("inserBoard.do step 1-5");
					if(wordService.selectOne(wvo2) == null || wordService.selectOne(wvo2).getWordRatio() < 40) {
						wvo2.setWordFound(wordFound);
						wvo2.setWordRatio(wordFound/wordArray.length*100);
						preWordVOList.add(wvo2);
						System.out.println("단어 발견 빈도 40% 미만. preWordVOList에 추가함");
					}
				}
			}
			// 1-6. count 기준으로 내림차순 정렬
			System.out.println("inserBoard.do step 1-6");
			bubbleSort(preWordVOList);
			System.out.println("preWrdVOList 정렬 완료");
			// 7. 비교작업 시작
			System.out.println("inserBoard.do step 1-7");
			for(int j=0; j<allBoardList.size(); j++) {
			// 비교 게시글 2, 4, 5, 6 작업 수행
				// 게시글 내용을 공백으로 구분하여 wordArray 배열에 담기
				System.out.println("inserBoard.do step 1-7-" + (j + 1));
				BoardVO bvo2 = new BoardVO();
				System.out.println("allBoardList.get(" + j + ").getBoardContent(): " + allBoardList.get(j).getBoardContent());
				String[] wordArray2 = allBoardList.get(j).getBoardContent().split(" ");
				// wordArray 배열을 순회하며 해당 단어가 몇 번 나오는지 알아내기
				ArrayList<WordVO> compareWordVOList = new ArrayList<WordVO>();
				for(int i=0; i<wordArray2.length; i++) {
					int wordFound=0;
					// compareWordVOList에 아직 없는 단어라면
					boolean foundFlag = false;
					for(WordVO v : compareWordVOList) {
						if(wordArray2[i].equals(v.getWordWord())){
							foundFlag = true;
						}
					}
					if(!foundFlag) {
						WordVO wvo2 = new WordVO();
						for(int a=0; a<wordArray2.length; a++) {
							wvo2.setWordWord(wordArray2[i]);
							if(wordArray2[i].equals(wordArray2[a])) {
								wordFound++;
								System.out.println("compare 내부 일치하는 단어 발견");
							}
						}
						// 단어별로 w_percentage < 40 면 단어, 횟수, 비중을 wordVO에 담기 -> compareWordVOList에 담기
						if(wordService.selectOne(wvo2) == null || wordService.selectOne(wvo2).getWordRatio() < 40) {
							wvo2.setWordFound(wordFound);
							wvo2.setWordRatio(wordFound/wordArray2.length*100);
							compareWordVOList.add(wvo2);
							System.out.println("단어 발견 빈도 40% 미만. compareWordVOList에 추가함");
						}
					}
				}
				// 6. count 기준으로 내림차순 정렬
				for(WordVO v : compareWordVOList) {
					System.out.println(v.getWordWord() + " / " + v.getWordFound());
				}
				System.out.println("↓");
				bubbleSort(compareWordVOList);
				System.out.println("compareWrdVOList 정렬 완료");
				for(WordVO v : compareWordVOList) {
					System.out.println(v.getWordWord() + " / " + v.getWordFound());
				}
				// preWordVOList와 compareWordVOList를 비교하기 시작
				int repetition = 0;
				int totalWord = 0;
				int foundWordCount = 0;
				for(int b=0; b<compareWordVOList.size(); b++) {
					boolean foundFlag = false;
					totalWord += compareWordVOList.get(b).getWordFound(); // 모든 단어의 개수의 합을 구함
					for(int a=0; a<preWordVOList.size(); a++) {
						// 같은 단어를 발견하면
						if(compareWordVOList.get(b).getWordWord()
								.equals(preWordVOList.get(a).getWordWord())) {
							repetition += compareWordVOList.get(b).getWordFound();
							foundFlag = true;
							System.out.println("pre-compare 일치하는 단어 발견");
						}
					}
					if(foundFlag) {
						System.out.println("같은 단어 발견됨");
						foundWordCount ++;
					}
				}
				if(foundWordCount >= 2) {
					// RELATED 테이블에 INSERT : repetition은 클수록, importance는 작을수록 연관도 높음)
					RelatedVO rvo = new RelatedVO();
					rvo.setOriginalBoardNum(preNum);
					rvo.setRelatedBoardNum(allBoardList.get(j).getBoardNum());
					System.out.println("repetition: " + repetition);
					System.out.println("totalWord: " + totalWord);
					rvo.setRelatedRepetition(repetition); // 중복 발견되므로 나누기 2
					rvo.setRelatedImportance((int)Math.round((repetition * 1.0) / (totalWord * 1.0) * 100)); // 전체중의 연관 단어 비율
					System.out.println("foundWordCount: " + foundWordCount + " / INSERTING rvo: " + rvo);
					relatedService.insert(rvo);
				}
			}
			// 전체 게시글에 대해 1-7 작업을 끝내면 종료
		
		// 가장 최근 게시글 B_NO 전달하기
		return "boardDetailView.do?boardNum=" + preNum;
	}
	
	// 게시글 목록 불러오기
	@ResponseBody
	@RequestMapping(value = "/getBoardList.do")
	public JsonArray sendBoardList() {
		System.out.println("getBoardList.do 진입");
		// JsonArray를 전달
		List boardList = null;
		BoardVO bvo = new BoardVO();
		boardList = boardService.selectAll(bvo); // 전체 게시글 목록
		System.out.println(boardList);
		JsonArray data = new Gson().toJsonTree(boardList).getAsJsonArray(); // JsonArry로 변경하여 반환
		return data;
	}
	
	// 관련 게시글 목록 불러오기
	@ResponseBody
	@RequestMapping(value = "/getRelatedList.do")
	public JsonArray sendRelatedList(RelatedVO rvo) {
		System.out.println("getRelatedList.do 진입");
		System.out.println("originalBoardNum: " + rvo.getOriginalBoardNum());
		// JsonArray를 전달
		List relatedList = null;
		relatedList = relatedService.selectAll(rvo); // 전체 게시글 목록
		System.out.println(relatedList);
		JsonArray data = new Gson().toJsonTree(relatedList).getAsJsonArray(); // JsonArry로 변경하여 반환
		return data;
	}
		
	// 버블정렬 가동
	public static void bubbleSort(ArrayList<WordVO> data) {
		bubbleSort(data, data.size());
	}
	
	// 버블정렬 실행
	private static void bubbleSort(ArrayList<WordVO> data, int size) {
		// round는 배열 크기 - 1 만큼 진행됨 
		for(int i = 1; i < size; i++) {
			// 각 라운드별 비교횟수는 배열 크기의 현재 라운드를 뺀 만큼 비교함
			for(int j = 0; j < size - i; j++) {
				/*
				 *  현재 원소가 다음 원소보다 클 경우
				 *  서로 원소의 위치를 교환한다. 
				 */
				if(data.get(j).getWordFound() > data.get(j+1).getWordFound()) {
					swap(data, j, j + 1);
				}
			}
		}
	}
	
	// 치환 로직
	private static void swap(ArrayList<WordVO> data, int i, int j) {
		WordVO tmp = data.get(i);
		data.set(i, data.get(j));
		data.set(j, tmp);
	}
}
