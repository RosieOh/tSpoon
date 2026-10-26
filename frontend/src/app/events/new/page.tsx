import type { Metadata } from "next";
import { Fresh } from "@/components/shell/Fresh";
import { EventForm } from "../event-form";

export const metadata: Metadata = { title: "행사 열기" };

export default function NewEventPage() {
  return (
    <Fresh>
      <EventForm />
    </Fresh>
  );
}
