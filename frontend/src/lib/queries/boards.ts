"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, isApiError } from "@/lib/api";
import { useLocalIdSet } from "@/lib/local-set";
import type { Board, BoardCategory, BoardInput, Comment, Page } from "@/lib/types";
import { boardQuery, boardsQuery, commentsQuery } from "./keys";
import { nextPageParam, PAGE_SIZE } from "./paging";
import { useSession } from "./session";

export function useBoards(category?: BoardCategory | null, size = PAGE_SIZE) {
  const q = boardsQuery(category, size);
  return useInfiniteQuery({
    queryKey: q.key,
    queryFn: ({ pageParam, signal }) => api<Page<Board>>(q.path(pageParam), { signal }),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
  });
}

// 상세 조회는 조회수를 올리므로 창 포커스 때 다시 받지 않는다(기본값)
export function useBoard(id: number) {
  const q = boardQuery(id);
  return useQuery({
    queryKey: q.key,
    queryFn: ({ signal }) => api<Board>(q.path, { signal }),
    staleTime: 60_000,
  });
}

export function useComments(boardId: number) {
  const q = commentsQuery(boardId);
  return useQuery({
    queryKey: q.key,
    queryFn: ({ signal }) => api<Comment[]>(q.path, { signal }),
  });
}

export function useSaveBoard(id?: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: BoardInput) =>
      id ? api<Board>(`/api/boards/${id}`, { method: "PUT", body }) : api<Board>("/api/boards", { method: "POST", body }),
    onSuccess: (board) => {
      client.setQueryData(["board", board.id], board);
      client.invalidateQueries({ queryKey: ["boards"] });
    },
  });
}

export function useDeleteBoard() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/boards/${id}`, { method: "DELETE" }),
    onSuccess: (_, id) => {
      client.removeQueries({ queryKey: ["board", id] });
      client.invalidateQueries({ queryKey: ["boards"] });
    },
  });
}

/**
 * 좋아요. 서버가 "내가 눌렀는지"를 주지 않아 브라우저에 기록해 두고,
 * 409(이미 누름)·404(누른 적 없음)를 받으면 기록만 바로잡는다.
 */
export function useBoardLike(board: Board | undefined) {
  const client = useQueryClient();
  const { user } = useSession();
  const [liked, setLiked] = useLocalIdSet(user ? `tspoon:liked:${user.id}` : null);
  const isLiked = board ? liked.has(board.id) : false;

  const bump = (id: number, delta: number) =>
    client.setQueryData<Board>(["board", id], (b) => (b ? { ...b, likeCount: Math.max(0, b.likeCount + delta) } : b));

  const mutation = useMutation({
    mutationFn: ({ id, like }: { id: number; like: boolean }) =>
      api<void>(`/api/boards/${id}/like`, { method: like ? "POST" : "DELETE" }),
    onMutate: ({ id, like }) => {
      setLiked(id, like);
      bump(id, like ? 1 : -1);
    },
    onError: (error, { id, like }) => {
      bump(id, like ? -1 : 1);
      if (like && isApiError(error, 409)) setLiked(id, true);
      else if (!like && isApiError(error, 404)) setLiked(id, false);
      else setLiked(id, !like);
    },
    onSettled: () => client.invalidateQueries({ queryKey: ["boards"] }),
  });

  return {
    isLiked,
    toggle: () => board && mutation.mutate({ id: board.id, like: !isLiked }),
    error: mutation.error,
  };
}

export function useCreateComment(boardId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { content: string; parentCommentId?: number | null }) =>
      api<Comment>(`/api/boards/${boardId}/comments`, { method: "POST", body }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["comments", boardId] });
      client.setQueryData<Board>(["board", boardId], (b) => (b ? { ...b, commentCount: b.commentCount + 1 } : b));
    },
  });
}

export function useUpdateComment(boardId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      api<Comment>(`/api/comments/${id}`, { method: "PUT", body: { content } }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["comments", boardId] }),
  });
}

export function useDeleteComment(boardId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/comments/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["comments", boardId] });
      client.setQueryData<Board>(["board", boardId], (b) =>
        b ? { ...b, commentCount: Math.max(0, b.commentCount - 1) } : b,
      );
    },
  });
}
