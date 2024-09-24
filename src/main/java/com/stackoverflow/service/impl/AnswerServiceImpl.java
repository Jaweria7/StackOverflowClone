package com.stackoverflow.service.impl;

import com.stackoverflow.dto.answers.AnswerDetailsDTO;
import com.stackoverflow.dto.answers.AnswerRequestDTO;
import com.stackoverflow.entity.Answer;
import com.stackoverflow.entity.Question;
import com.stackoverflow.entity.User;
import com.stackoverflow.exception.ResourceNotFoundException;
import com.stackoverflow.repository.AnswerRepository;
import com.stackoverflow.repository.QuestionRepository;
import com.stackoverflow.repository.UserRepository;
import com.stackoverflow.service.AnswerService;
import com.stackoverflow.service.VoteService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnswerServiceImpl implements AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ModelMapper modelMapper;
    private final UserServiceImpl userService;
    private final VoteService voteService;


    @Autowired
    public AnswerServiceImpl(AnswerRepository answerRepository, QuestionRepository questionRepository, ModelMapper modelMapper, UserServiceImpl userService, VoteService voteService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.modelMapper = modelMapper;
        this.userService = userService;
        this.voteService = voteService;
    }

    public AnswerDetailsDTO createAnswer(AnswerRequestDTO answerRequestDTO, Long questionId, boolean isAiGenerated) {
        User user = userService.getLoggedInUser();

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Answer answer = modelMapper.map(answerRequestDTO, Answer.class);

        answer.setQuestion(question);
        answer.setCreatedAt(LocalDateTime.now());
        answer.setUpdatedAt(LocalDateTime.now());
        answer.setAuthor(user);
        answer.setAiGenerated(isAiGenerated);
        answer.setIsAccepted(false);
        Answer savedAnswer = answerRepository.save(answer);

        return modelMapper.map(savedAnswer, AnswerDetailsDTO.class);
    }

    @Override
    public AnswerDetailsDTO getAnswerById(Long answerId) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + answerId));

        AnswerDetailsDTO answerDetailsDTO = getAnswerDetailsDTO(answer);
        return answerDetailsDTO;
    }

    @Override
    public AnswerDetailsDTO updateAnswer(Long answerId, Long questionId, AnswerRequestDTO answerRequestDTO) {
        Answer existingAnswer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + answerId));

        existingAnswer.setBody(answerRequestDTO.getBody());
        existingAnswer.setUpdatedAt(LocalDateTime.now());

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        existingAnswer.setQuestion(question);

        Answer updatedAnswer = answerRepository.save(existingAnswer);
        AnswerDetailsDTO answerDetailsDTO = getAnswerDetailsDTO(updatedAnswer);

        return answerDetailsDTO;
    }

    @Override
    public Boolean deleteAnswer(Long answerId) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + answerId));

        answerRepository.deleteById(answerId);
        return true;
    }

    @Override
    public List<AnswerDetailsDTO> getAnswersByUserId(Long userId) {
        return answerRepository.findByAuthorId(userId).stream()
                .map(answer -> getAnswerDetailsDTO(answer))
                .collect(Collectors.toList());
    }

    @Override
    public AnswerDetailsDTO getAnswerDetailsDTO(Answer answer) {
        AnswerDetailsDTO answerDetailsDTO = modelMapper.map(answer, AnswerDetailsDTO.class);
        boolean upvoted = false;
        boolean downvoted = false;
        int upvotes = voteService.getAnswerUpvotes(answer.getId());
        int downvotes = voteService.getAnswerDownvotes(answer.getId());

        if (userService.isUserLoggedIn()) {
            User user = userService.getLoggedInUser();
            Integer status = answerRepository.getUserVoteStatus(answer.getId(), user.getId());
            if (status != null && status == 1) {
                upvoted = true;
            } else if (status != null && status == 0) {
                downvoted = true;
            }
        }

        answerDetailsDTO.setUpvotes(upvotes);
        answerDetailsDTO.setDownvotes(downvotes);
        answerDetailsDTO.setUpvoted(upvoted);
        answerDetailsDTO.setDownvoted(downvoted);

        return answerDetailsDTO;
    }

    @Override
    public Page<AnswerDetailsDTO> getSearchedAnswers(int page, int size, String sort, Long questionId) {
        Pageable pageable;

        switch (sort.toLowerCase()) {
            case "oldest":
                pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));  // ASC for oldest
                break;
            case "newest":
                pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")); // DESC for newest
                break;
            case "mostliked":
                return answerRepository.findAllAnswersOrderedByUpVotes(PageRequest.of(page, size), questionId)
                        .map(answer -> modelMapper.map(answer, AnswerDetailsDTO.class));
            default:
                pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));  // Default to newest
        }

        Page<Answer> answers = answerRepository.findAllByQuestionId(questionId, pageable);
        return answers.map(answer -> modelMapper.map(answer, AnswerDetailsDTO.class));
    }

    @Override
    public Boolean isAiGeneratedAnswer(String answer) {
        return answer.contains("this is ai");
    }

}
