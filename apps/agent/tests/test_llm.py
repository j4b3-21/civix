from app.config.settings import Settings
from app.services.llm.base import AnthropicProvider, LLMBudgetExceeded, llm_context


def test_local_openai_compatible_provider_is_the_default():
    assert Settings().llm_provider == "openai_compatible"


def test_task_budget_is_enforced_before_a_request():
    provider = AnthropicProvider("key", "model", 0.003, 0.015, max_task=0.001, max_daily_user=5)
    with llm_context(task_id="task-1", user_id="user-1"):
        try:
            provider._authorize({"request": "test"})
        except LLMBudgetExceeded:
            return
    raise AssertionError("expected task budget to reject the request")
