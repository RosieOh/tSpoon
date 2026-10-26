"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { DetailScreenSkeleton } from "@/components/shell/Screen";
import { ErrorState } from "@/components/ui/States";
import { useEvent } from "@/lib/queries/events";
import { EventForm } from "../../event-form";

export default function EditEventPage() {
  return (
    <Suspense fallback={<DetailScreenSkeleton fallback="/events" />}>
      <EditEvent />
    </Suspense>
  );
}

function EditEvent() {
  const { id } = useParams<{ id: string }>();
  const event = useEvent(Number(id));
  if (event.isPending) return <DetailScreenSkeleton fallback={`/events/${id}`} />;
  if (event.isError) {
    return (
      <>
        <NavBar left={<BackButton fallback="/events" />} />
        <ErrorState error={event.error} onRetry={() => event.refetch()} />
      </>
    );
  }
  return <EventForm key={event.data.id} event={event.data} />;
}
