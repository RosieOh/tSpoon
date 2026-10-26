import { ButtonLink } from "@/components/ui/Button";
import { ResultView } from "@/components/ui/Result";

export default function NotFound() {
  return (
    <ResultView emoji="search" title="페이지를 찾을 수 없어요" description="주소가 바뀌었거나 사라진 페이지예요">
      <ButtonLink href="/" variant="weak" size="md" className="mt-8">
        홈으로 가기
      </ButtonLink>
    </ResultView>
  );
}
