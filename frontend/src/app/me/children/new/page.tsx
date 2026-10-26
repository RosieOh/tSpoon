import type { Metadata } from "next";
import { Fresh } from "@/components/shell/Fresh";
import { ChildForm } from "../child-form";

export const metadata: Metadata = { title: "아이 등록하기" };

export default function NewChildPage() {
  return (
    <Fresh>
      <ChildForm />
    </Fresh>
  );
}
