import type { Metadata } from "next";
import { Fresh } from "@/components/shell/Fresh";
import { ContentForm } from "../content-form";

export const metadata: Metadata = { title: "콘텐츠 만들기" };

export default function NewContentPage() {
  return (
    <Fresh>
      <ContentForm />
    </Fresh>
  );
}
