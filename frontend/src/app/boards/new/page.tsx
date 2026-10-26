import type { Metadata } from "next";
import { Fresh } from "@/components/shell/Fresh";
import { BoardForm } from "../board-form";

export const metadata: Metadata = { title: "글쓰기" };

export default function NewBoardPage() {
  return (
    <Fresh>
      <BoardForm />
    </Fresh>
  );
}
