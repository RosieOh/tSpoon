"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Answer, Page, Question, QuestionCategory, QuestionInput, QuestionStatus, QuestionSummary } from "@/lib/types";
import { questionQuery, questionsQuery } from "./keys";
import { nextPageParam, PAGE_SIZE } from "./paging";

export function useQuestions(
  filter: { category?: QuestionCategory | null; status?: QuestionStatus | null },
  size = PAGE_SIZE,
) {
  const q = questionsQuery(filter, size);
  return useInfiniteQuery({
    queryKey: q.key,
    queryFn: ({ pageParam, signal }) => api<Page<QuestionSummary>>(q.path(pageParam), { signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

export function useQuestion(id: number) {
  const q = questionQuery(id);
  return useQuery({
    queryKey: q.key,
    queryFn: ({ signal }) => api<Question>(q.path, { signal }),
    staleTime: 60_000,
  });
}

function useQuestionMutations(id: number) {
  const client = useQueryClient();
  const refresh = () => {
    client.invalidateQueries({ queryKey: ["question", id] });
    client.invalidateQueries({ queryKey: ["questions"] });
  };
  return { client, refresh };
}

export function useSaveQuestion(id?: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: QuestionInput) =>
      id
        ? api<Question>(`/api/questions/${id}`, { method: "PUT", body })
        : api<Question>("/api/questions", { method: "POST", body }),
    onSuccess: (question) => {
      client.setQueryData(["question", question.id], question);
      client.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

export function useDeleteQuestion() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/questions/${id}`, { method: "DELETE" }),
    onSuccess: (_, id) => {
      client.removeQueries({ queryKey: ["question", id] });
      client.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

export function useCloseQuestion(id: number) {
  const { refresh } = useQuestionMutations(id);
  return useMutation({
    mutationFn: () => api<void>(`/api/questions/${id}/close`, { method: "POST" }),
    onSuccess: refresh,
  });
}

export function useAnswerQuestion(id: number) {
  const { refresh } = useQuestionMutations(id);
  return useMutation({
    mutationFn: (content: string) => api<Answer>(`/api/questions/${id}/answers`, { method: "POST", body: { content } }),
    onSuccess: refresh,
  });
}

export function useAcceptAnswer(id: number) {
  const { client } = useQuestionMutations(id);
  return useMutation({
    mutationFn: (answerId: number) => api<Question>(`/api/questions/${id}/answers/${answerId}/accept`, { method: "POST" }),
    onSuccess: (question) => {
      client.setQueryData(["question", id], question);
      client.invalidateQueries({ queryKey: ["questions"] });
    },
  });
}

export function useUpdateAnswer(id: number) {
  const { refresh } = useQuestionMutations(id);
  return useMutation({
    mutationFn: ({ answerId, content }: { answerId: number; content: string }) =>
      api<Answer>(`/api/answers/${answerId}`, { method: "PUT", body: { content } }),
    onSuccess: refresh,
  });
}

export function useDeleteAnswer(id: number) {
  const { refresh } = useQuestionMutations(id);
  return useMutation({
    mutationFn: (answerId: number) => api<void>(`/api/answers/${answerId}`, { method: "DELETE" }),
    onSuccess: refresh,
  });
}
